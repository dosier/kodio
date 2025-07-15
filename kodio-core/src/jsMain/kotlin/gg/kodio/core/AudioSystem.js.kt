package gg.kodio.core

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaDeviceInfo
import org.w3c.dom.mediacapture.MediaStreamConstraints
import web.media.devices.MediaDeviceKind

/**
 * JS implementation of the AudioSystem using the Web Audio API.
 * This requires a secure context (HTTPS) to function in a browser.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    // Ensures we only request permission once.
    private var permissionGranted: Boolean? = null

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
        if (!ensurePermissions()) return emptyList()
        val devices = window.navigator.mediaDevices.enumerateDevices().await()
        return devices.filter { it.kind == type }
    }

    private suspend fun ensurePermissions(): Boolean {
        if (permissionGranted == true) return true
        // Calling getUserMedia is how you trigger the permission prompt.
        try {
            window.navigator.mediaDevices.getUserMedia(MediaStreamConstraints(audio = true)).await()
            permissionGranted = true
            return true
        } catch (e: Exception) {
            console.error("Audio permission denied.", e)
            permissionGranted = false
            return false
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