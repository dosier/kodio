package space.kodio.core

import platform.AVFAudio.AVAudioSession

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
        val audioSession = AVAudioSession.Companion.sharedInstance()
        audioSession.configureCategoryRecord()
        audioSession.activate()
        if (requestedDevice != null)
            audioSession.setPreferredInput(requestedDevice)
    }
}