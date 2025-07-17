package space.kodio.core

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaDeviceInfo
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager
import web.media.devices.MediaDeviceKind

/**
 * JS implementation of the AudioSystem using the Web Audio API.
 * This requires a secure context (HTTPS) to function in a browser.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager
        get() = JsAudioPermissionManager

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        listDevices(MediaDeviceKind.audioinput)
            .map(::toInputDevice)

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        listDevices(MediaDeviceKind.audiooutput)
            .map(::toOutputDevice)

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession =
        JsAudioRecordingSession(device)

    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        JsAudioPlaybackSession(device)

    private suspend fun listDevices(type: MediaDeviceKind): List<MediaDeviceInfo> {
        return try {
            permissionManager.withMicrophonePermission {
                val devices = window.navigator
                    .mediaDevices
                    .enumerateDevices()
                    .await()
                devices.filter { it.kind == type }
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