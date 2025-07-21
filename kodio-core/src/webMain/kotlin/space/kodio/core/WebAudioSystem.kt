package space.kodio.core

import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager
import web.mediadevices.MediaDeviceInfo
import web.mediadevices.MediaDeviceKind
import web.mediadevices.audioinput
import web.mediadevices.audiooutput
import web.mediadevices.enumerateDevices
import web.navigator.navigator

open class WebAudioSystem : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager =
        WebAudioPermissionManager()

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        listDevices(MediaDeviceKind.audioinput)
            .map(::toInputDevice)

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        listDevices(MediaDeviceKind.audiooutput)
            .map(::toOutputDevice)

    override suspend fun createRecordingSession(requestedDevice: AudioDevice.Input?): AudioRecordingSession =
        WebAudioRecordingSession(requestedDevice)

    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession =
        WebAudioPlaybackSession()

    private suspend fun listDevices(type: MediaDeviceKind): List<MediaDeviceInfo> {
        return try {
            permissionManager.withMicrophonePermission {
                navigator
                    .mediaDevices
                    .enumerateDevices()
                    .toList()
                    .filter { it.kind == type }
            }
        } catch (_: AudioPermissionDeniedException) {
            emptyList()
        }
    }
}

private fun toOutputDevice(info: MediaDeviceInfo): AudioDevice.Output = AudioDevice.Output(
    id = info.deviceId,
    name = info.label,
    formatSupport = AudioFormatSupport.Unknown
)

private fun toInputDevice(info: MediaDeviceInfo): AudioDevice.Input = AudioDevice.Input(
    id = info.deviceId,
    name = info.label,
    formatSupport = AudioFormatSupport.Unknown
)