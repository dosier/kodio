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

        audioQueue = MacosAudioQueue.createOutput(
            format = playbackFormat,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )

        if (requestedDevice != null) {
            audioQueue.setPropertyValue(CurrentDevice, requestedDevice.id)
        }

        // Always return the device format so Base convertAudio matches the queue
        // (including mono→stereo). Transport feeds converted PCM only.
        return playbackFormat
    }

    private fun isDeviceFriendly(format: AudioFormat): Boolean = when (val enc = format.encoding) {
        is SampleEncoding.PcmFloat -> true
        is SampleEncoding.PcmInt ->
            enc.bitDepth == IntBitDepth.Sixteen &&
                enc.signed &&
                enc.endianness == Endianness.Little
    }

    private fun toDevicePlaybackFormat(format: AudioFormat): AudioFormat {
        val channels = if (format.channels == Channels.Mono) Channels.Stereo else format.channels
        if (isDeviceFriendly(format)) {
            return format.copy(channels = channels)
        }
        return AudioFormat(
            sampleRate = format.sampleRate,
            channels = channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
    }

    override fun createAudioSink(format: AudioFormat): AudioSink = AudioQueueSink()

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("MacosAudioPlaybackSession uses transport playback")
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
            val transport = currentTransport()
            if (transport != null && transport.seekEpoch != seekEpoch) return

            var written = 0
            var attempts = 0
            while (written < length && attempts++ < 32) {
                val n = audioQueue.tryPrimeBuffer(data, offset + written, length - written)
                if (n <= 0) break
                written += n
            }
            if (!queueStarted && written > 0) {
                runCatching {
                    audioQueue.start()
                    queueStarted = true
                }
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
