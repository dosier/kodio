package space.kodio.core

/**
 * IOS implementation for [AppleAudioRecordingSession].
 *
 * @param requestedDevice The requested input device to record from.
 */
class IosAudioRecordingSession(
    requestedDevice: AudioDevice.Input?,
    format: AudioFormat = DefaultRecordingAudioFormat
) : AppleAudioRecordingSession(requestedDevice, format)