package space.kodio.core

import kotlinx.coroutines.await
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager

/**
 * JS implementation of the AudioSystem using the Web Audio API.
 * This requires a secure context (HTTPS) to function in a browser.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager
        get() = WasmJsAudioPermissionManager

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        listDevices(MediaDeviceKindAudioInput)
            .map(::toInputDevice)

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        listDevices(MediaDeviceKindAudioOutput)
            .map(::toOutputDevice)

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession =
        WasmJsAudioRecordingSession(device)

    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        WasmJsAudioPlaybackSession(device)

    private suspend fun listDevices(type: MediaDeviceKind): List<MediaDeviceInfo> {
        return try {
            permissionManager.withMicrophonePermission {
                val devices = navigator
                    .mediaDevices
                    .enumerateDevices()
                    .await<JsArray<MediaDeviceInfo>>()
                devices.toList().filter { it.kind == type }
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