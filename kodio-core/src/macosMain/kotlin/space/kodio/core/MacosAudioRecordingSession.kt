package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.SendChannel
import space.kodio.core.MacosAudioQueueProperty.CurrentDevice
import space.kodio.core.util.namedLogger

private val logger = namedLogger("MacosRecording")

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
        val preferred = requestedFormat ?: DefaultRecordingInt16
        for (candidate in recordingFormatCandidates(preferred)) {
            tryCreateAudioQueue(candidate)?.let { resolved ->
                if (resolved != preferred) {
                    logger.info {
                        "Using recording format $resolved (requested $preferred was not accepted by AudioQueue)"
                    }
                }
                val deviceUID: String? = requestedDevice?.id ?: getDefaultInputDeviceUID()
                if (deviceUID != null) {
                    logger.info { "Setting AudioQueue input device to: $deviceUID" }
                    audioQueue.setPropertyValue(CurrentDevice, deviceUID)
                }
                return resolved
            }
        }
        error("No supported audio format found for this device")
    }

    /**
     * Ordered fallbacks: preferred format, then device default when CoreAudio reports known
     * virtual formats, then [DefaultRecordingInt16].
     */
    private fun recordingFormatCandidates(preferred: AudioFormat): List<AudioFormat> =
        buildSet {
            add(preferred)
            when (val fs = requestedDevice?.formatSupport) {
                is AudioFormatSupport.Known -> {
                    if (preferred !in fs.supportedFormats) {
                        logger.warn {
                            "Requested format $preferred is not among ${fs.supportedFormats.size} virtual " +
                                "formats reported for input device \"${requestedDevice?.name}\" — trying " +
                                "AudioQueue anyway, then device default and platform default"
                        }
                    }
                    add(fs.defaultFormat)
                }
                else -> Unit
            }
            add(DefaultRecordingInt16)
        }.toList()

    private fun tryCreateAudioQueue(format: AudioFormat): AudioFormat? {
        return try {
            audioQueue = MacosAudioQueue.createInput(
                format = format,
                bufferCount = bufferCount,
                bufferDurationSec = bufferDurationSec
            )
            format
        } catch (e: Exception) {
            logger.warn(e) { "AudioQueue input creation failed for format $format" }
            null
        }
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        audioQueue.start()
        audioQueue.streamTo(channel)
    }

    override fun cleanup() {
        audioQueue.dispose(inImmediate = true)
    }

    override suspend fun pauseRecording() {
        // Native pause: AudioQueuePause halts capture but keeps the queue + buffers
        // allocated, so resume only needs an AudioQueueStart call.
        runCatching { audioQueue.pause() }.onFailure {
            logger.warn(it) { "AudioQueuePause failed; falling back to dispose+reprepare" }
            super.pauseRecording()
        }
    }

    override suspend fun resumeRecording() {
        // Just AudioQueueStart on the same queue. If the queue was disposed
        // (e.g. fallback path above), re-prepare from scratch.
        runCatching { audioQueue.start() }.onFailure {
            logger.warn(it) { "AudioQueueStart on resume failed; re-preparing queue" }
            super.resumeRecording()
        }
    }
}
