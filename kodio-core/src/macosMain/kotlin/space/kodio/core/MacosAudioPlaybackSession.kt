package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import space.kodio.core.MacosAudioQueueProperty.CurrentDevice

/**
 * Mac OS implementation for [AVAudioPlaybackSession] using Core Audio AudioQueue (output).
 */
@ExperimentalForeignApi
class MacosAudioPlaybackSession(
    private val requestedDevice: AudioDevice.Output? = null,
    private val bufferDurationSec: Double = 0.02, // ≈20ms buffer
    private val bufferCount: Int = 3              // triple-buffered
) : BaseAudioPlaybackSession() {

    private lateinit var audioQueue: MacosAudioQueue.Writable
    private var paused = false

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        audioQueue = MacosAudioQueue.createOutput(
            format = format,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )
        val requestedDevice: AudioDevice.Output? = requestedDevice
        if (requestedDevice != null)
            audioQueue.setPropertyValue(CurrentDevice, requestedDevice.id)
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        audioQueue.start()
        audioQueue.streamFrom(audioFlow)
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