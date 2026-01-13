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
        
        // Determine output format - most output devices require stereo
        outputFormat = if (format.channels == Channels.Mono) {
            format.copy(channels = Channels.Stereo)
        } else {
            format
        }
        logger.debug { "Output format: $outputFormat" }
        
        audioQueue = MacosAudioQueue.createOutput(
            format = outputFormat!!,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )
        logger.debug { "AudioQueue created successfully" }
        
        if (requestedDevice != null) {
            logger.debug { "Setting device: ${requestedDevice.name} (${requestedDevice.id})" }
            audioQueue.setPropertyValue(CurrentDevice, requestedDevice.id)
        }
        
        // Return the INPUT format to skip the slow BigDecimal-based convertAudio()
        // We'll do our own fast mono-to-stereo conversion in playBlocking
        return format
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
