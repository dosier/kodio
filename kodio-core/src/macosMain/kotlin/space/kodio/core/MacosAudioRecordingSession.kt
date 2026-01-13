package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.SendChannel
import space.kodio.core.MacosAudioQueueProperty.CurrentDevice

/**
 * macOS implementation for [AVAudioRecordingSession] using AudioQueue input.
 */
@OptIn(ExperimentalForeignApi::class)
class MacosAudioRecordingSession(
    private val requestedDevice: AudioDevice.Input?,
    private val requestedFormat: AudioFormat? = requestedDevice?.formatSupport?.defaultFormat,
    private val bufferDurationSec: Double = 0.05, // ≈50ms buffer - more headroom for processing
    private val bufferCount: Int = 5              // 5 buffers - better resilience to delays
) : BaseAudioRecordingSession() {

    private lateinit var audioQueue: MacosAudioQueue.ReadOnly

    override suspend fun prepareRecording(): AudioFormat {
        val format = requestedFormat?:DefaultRecordingInt16
        audioQueue = MacosAudioQueue.createInput(
            format = format,
            bufferCount = bufferCount,
            bufferDurationSec = bufferDurationSec
        )
        val device: AudioDevice.Input? = requestedDevice
        if (device != null)
            audioQueue.setPropertyValue(CurrentDevice, device.id)
        return format
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        audioQueue.start()
        audioQueue.streamTo(channel)
    }

    override fun cleanup() {
        audioQueue.dispose(inImmediate = true)
    }
}
