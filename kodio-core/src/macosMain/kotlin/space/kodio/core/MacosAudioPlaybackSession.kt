package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import space.kodio.core.MacosAudioQueueProperty.CurrentDevice
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.TransportController
import space.kodio.core.util.namedLogger
import kotlin.time.Duration

private val logger = namedLogger("PlaybackSession")

/**
 * macOS AudioQueue playback fed by [PlaybackBuffer] transport.
 *
 * Seek uses seek-quiesce + [AudioQueueStop]`(true)` (not channel-closing
 * [MacosAudioQueue.markNotRunning], and not [AudioQueueFlush] which is EOS drain).
 */
@ExperimentalForeignApi
class MacosAudioPlaybackSession(
    private val requestedDevice: AudioDevice.Output? = null,
    private val bufferDurationSec: Double = 0.05,
    private val bufferCount: Int = 5
) : BaseAudioPlaybackSession() {

    private lateinit var audioQueue: MacosAudioQueue.Writable
    private var outputFormat: AudioFormat? = null
    private var queueStarted: Boolean = false

    override fun useTransportPlayback(): Boolean = true

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        logger.debug { "preparePlayback called with format: $format" }

        if (::audioQueue.isInitialized) {
            audioQueue.dispose(inImmediate = true)
            queueStarted = false
        }

        val playbackFormat = toDevicePlaybackFormat(format)
        outputFormat = playbackFormat
        logger.info {
            "preparePlayback deviceFormat=$playbackFormat " +
                "bpf=${playbackFormat.bytesPerFrame} from source=$format"
        }

        audioQueue = MacosAudioQueue.createOutput(
            format = playbackFormat,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )

        if (requestedDevice != null) {
            audioQueue.setPropertyValue(CurrentDevice, requestedDevice.id)
        }

        // Return the queue format so Base convertAudio matches what AudioQueue expects.
        // Prefer keeping mono/24-bit/float as-is — forced mono→stereo used the slow
        // BigDecimal path and caused realtime underruns (audible distortion).
        return playbackFormat
    }

    private fun isDeviceFriendly(format: AudioFormat): Boolean {
        val enc = format.encoding
        if (enc is SampleEncoding.PcmFloat) {
            return enc.layout == SampleLayout.Interleaved &&
                (enc.precision == FloatPrecision.F32 || enc.precision == FloatPrecision.F64)
        }
        if (enc is SampleEncoding.PcmInt) {
            return enc.signed &&
                enc.packed &&
                enc.endianness == Endianness.Little &&
                enc.layout == SampleLayout.Interleaved &&
                (enc.bitDepth == IntBitDepth.Sixteen ||
                    enc.bitDepth == IntBitDepth.TwentyFour ||
                    enc.bitDepth == IntBitDepth.ThirtyTwo)
        }
        return false
    }

    private fun toDevicePlaybackFormat(format: AudioFormat): AudioFormat {
        if (isDeviceFriendly(format)) return format
        // Fallback: interleaved float32 at the same rate/channel count.
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    override fun createAudioSink(format: AudioFormat): AudioSink = AudioQueueSink()

    @Deprecated("Transport playback", level = DeprecationLevel.HIDDEN)
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("MacosAudioPlaybackSession uses transport playback")
    }

    override fun deviceQueuedFrames(): Long {
        if (!::audioQueue.isInitialized) return 0L
        val bpf = (outputFormat?.bytesPerFrame ?: 1).coerceAtLeast(1)
        return audioQueue.inFlightBytes / bpf
    }

    override fun readDeviceMediaPosition(
        mediaBase: Duration,
        transport: TransportController,
    ): Duration = transport.buffer.format.durationOfByteOffset(transport.readCursor)

    override fun onTransportPaused() {
        if (::audioQueue.isInitialized) {
            runCatching { audioQueue.pause() }
        }
    }

    override fun onTransportResumed() {
        if (::audioQueue.isInitialized) {
            runCatching { audioQueue.start() }
            queueStarted = true
        }
    }

    override fun onTransportStopped() {
        if (::audioQueue.isInitialized) {
            runCatching { audioQueue.stop(inImmediate = true) }
            runCatching { audioQueue.dispose(inImmediate = true) }
            queueStarted = false
        }
    }

    override suspend fun ensureDeviceReadyForTransport(format: AudioFormat): AudioFormat {
        if (::audioQueue.isInitialized) return outputFormat ?: format
        return preparePlayback(format)
    }

    override fun onPause() = onTransportPaused()
    override fun onResume() = onTransportResumed()
    override fun onStop() = onTransportStopped()

    private inner class AudioQueueSink : AudioSink {
        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            if (!::audioQueue.isInitialized || length <= 0) return
            // Must block for AudioQueue buffer availability. Transport advances the
            // soft cursor by [length] after write returns; non-blocking primes that
            // only fill the free pool skip the rest of the PCM (first-note-only bug).
            var written = 0
            while (written < length) {
                val transport = currentTransport()
                if (transport != null && transport.seekEpoch != seekEpoch) return

                val n = audioQueue.enqueueBlocking(data, offset + written, length - written)
                if (n <= 0) return
                written += n
                if (!queueStarted) {
                    runCatching {
                        audioQueue.start()
                        queueStarted = true
                        logger.debug { "AudioQueueSink started after first enqueue ($written bytes)" }
                    }.onFailure {
                        logger.error(it) { "AudioQueueStart failed: ${it.message}" }
                        throw it
                    }
                }
            }
            logger.trace {
                "AudioQueueSink.write enqueued=$written mediaOffset=$mediaByteOffset epoch=$seekEpoch"
            }
        }

        override fun flush(seekEpoch: Long) {
            if (!::audioQueue.isInitialized) return
            // Seek discard: quiesce + Stop(true); keep pool channels open.
            runCatching { audioQueue.seekDiscardImmediate() }
            queueStarted = false
            logger.debug { "AudioQueueSink.flush epoch=$seekEpoch via seekDiscardImmediate" }
            if (state.value is AudioPlaybackSession.State.Playing) {
                runCatching {
                    audioQueue.start()
                    queueStarted = true
                }
            }
        }
    }
}
