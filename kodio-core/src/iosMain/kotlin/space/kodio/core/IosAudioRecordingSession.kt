package space.kodio.core

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.currentRoute
import platform.AVFAudio.inputNumberOfChannels
import platform.AVFAudio.outputNumberOfChannels
import platform.AVFAudio.sampleRate
import space.kodio.core.util.namedLogger

private val log = namedLogger("IosAudioRecording")

/**
 * IOS implementation for [AVAudioRecordingSession].
 *
 * @param requestedDevice The requested input device to record from.
 */
class IosAudioRecordingSession(
    private val requestedDevice: AudioDevice.Input?,
    format: AudioFormat = DefaultAppleRecordingAudioFormat
) : AVAudioRecordingSession(format) {

    override fun prepareAudioSession() {
        log.info { "prepareAudioSession(): requestedDevice=$requestedDevice" }
        val audioSession = AVAudioSession.Companion.sharedInstance()
        audioSession.configureCategoryRecord()
        audioSession.activate()
        if (requestedDevice != null)
            audioSession.setPreferredInput(requestedDevice)
        log.info {
            "Audio session after record config: category=${audioSession.category}, " +
                "sampleRate=${audioSession.sampleRate}, " +
                "inputChannels=${audioSession.inputNumberOfChannels}, " +
                "outputChannels=${audioSession.outputNumberOfChannels}, " +
                "currentRoute.inputs=${audioSession.currentRoute.inputs}, " +
                "currentRoute.outputs=${audioSession.currentRoute.outputs}"
        }
    }
}