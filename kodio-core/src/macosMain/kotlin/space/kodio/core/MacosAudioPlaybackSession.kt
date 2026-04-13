package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.map
import space.kodio.core.MacosAudioQueueProperty.CurrentDevice
import space.kodio.core.util.namedLogger

private val logger = namedLogger("PlaybackSession")

/**
 * Mac OS implementation for [AudioPlaybackSession] using Core Audio AudioQueue (output).
 */
@ExperimentalForeignApi
class MacosAudioPlaybackSession(
    private val requestedDevice: AudioDevice.Output? = null,
    private val bufferDurationSec: Double = 0.05, // ≈50ms buffer
    private val bufferCount: Int = 5              // 5 buffers for smoother playback
) : BaseAudioPlaybackSession() {

    private lateinit var audioQueue: MacosAudioQueue.Writable
    private var paused = false
    private var outputFormat: AudioFormat? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        logger.debug { "preparePlayback called with format: $format" }

        if (::audioQueue.isInitialized) {
            audioQueue.dispose(inImmediate = true)
        }

        val playbackFormat = toDevicePlaybackFormat(format)
        outputFormat = playbackFormat
        logger.debug { "Output format: $outputFormat" }
        
        audioQueue = MacosAudioQueue.createOutput(
            format = playbackFormat,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )
        logger.debug { "AudioQueue created successfully" }
        
        if (requestedDevice != null) {
            logger.debug { "Setting device: ${requestedDevice.name} (${requestedDevice.id})" }
            audioQueue.setPropertyValue(CurrentDevice, requestedDevice.id)
        }

        if (isDeviceFriendly(format)) {
            // For well-supported formats, return the INPUT format to skip
            // the slow BigDecimal-based convertAudio(); playBlocking handles
            // fast mono-to-stereo duplication when needed.
            return format
        }
        // For formats CoreAudio AudioQueue may not handle reliably
        // (24-bit packed int, unsigned, big-endian, etc.), return the
        // normalized Float32 format so BaseAudioPlaybackSession.play()
        // runs convertAudio() to produce device-compatible bytes.
        return playbackFormat
    }

    /**
     * Float32 and signed-16-bit-LE are the two formats CoreAudio
     * AudioQueue handles without issue. Everything else gets
     * normalized to Float32 stereo.
     */
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

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        logger.debug { "playBlocking: input=${audioFlow.format}, output=$outputFormat" }
        
        // Fast mono-to-stereo conversion (bypasses slow BigDecimal convertAudio)
        val playbackFlow = if (audioFlow.format.channels == Channels.Mono && outputFormat?.channels == Channels.Stereo) {
            logger.debug { "Doing fast mono-to-stereo conversion" }
            val bytesPerSample = audioFlow.format.bytesPerSample
            AudioFlow(
                format = outputFormat!!,
                data = audioFlow.map { chunk ->
                    // Duplicate each sample for left and right channels
                    val stereo = ByteArray(chunk.size * 2)
                    var src = 0
                    var dst = 0
                    while (src < chunk.size) {
                        // Copy sample to left channel
                        repeat(bytesPerSample) { b ->
                            stereo[dst + b] = chunk[src + b]
                        }
                        // Copy same sample to right channel
                        repeat(bytesPerSample) { b ->
                            stereo[dst + bytesPerSample + b] = chunk[src + b]
                        }
                        src += bytesPerSample
                        dst += bytesPerSample * 2
                    }
                    stereo
                }
            )
        } else {
            audioFlow
        }
        
        // Stream data to queue - start() will be called after initial buffers are primed
        logger.debug { "Starting stream (queue will start after priming)" }
        audioQueue.streamFromWithPriming(playbackFlow, bufferCount)
        logger.debug { "streamFrom completed" }
    }

    override fun onPause() {
        if (paused) return
        audioQueue.pause()
        paused = true
    }

    override fun onResume() {
        if (!paused) return
        audioQueue.start()
        paused = false
    }

    override fun onStop() {
        audioQueue.stop(inImmediate = true)
    }
}
