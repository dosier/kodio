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

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession =
        WebAudioRecordingSession(device)

    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        WebAudioPlaybackSession(device)

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