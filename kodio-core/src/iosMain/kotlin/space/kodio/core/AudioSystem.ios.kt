package space.kodio.core

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.currentRoute
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager

/**
 * IOS implementation for [AudioSystem].
 *
 * @see IosAudioPlaybackSession for playback session implementation.
 * @see IosAudioRecordingSession for recording session implementation.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    private val audioSession get() = AVAudioSession.sharedInstance()

    override val permissionManager: AudioPermissionManager
        get() = IosAudioPermissionManager

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        return try {
            permissionManager.withMicrophonePermission {
                audioSession.availableInputs
                    ?.filterIsInstance<AVAudioSessionPortDescription>()
                    ?.map { port ->
                        AudioDevice.Input(
                            id = port.UID(),
                            name = port.portName(),
                            formatSupport = AudioFormatSupport.Unknown
                        )
                    }
                    ?: emptyList()
            }
        } catch (_: AudioPermissionDeniedException) {
            emptyList()
        }
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        return audioSession.currentRoute.outputs
            .filterIsInstance<AVAudioSessionPortDescription>()
            .map { port ->
                AudioDevice.Output(
                    id = port.UID(),
                    name = port.portName(),
                    formatSupport = AudioFormatSupport.Unknown
                )
            }
    }

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession =
        permissionManager.withMicrophonePermission { IosAudioRecordingSession(device) }

    /**
     * In IOS, there is no control over the output device, so we ignore it.
     */
    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        IosAudioPlaybackSession()
}