import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStreamConstraints

/**
 * JS implementation of the AudioSystem using the Web Audio API.
 * This requires a secure context (HTTPS) to function in a browser.
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    // Ensures we only request permission once.
    private var permissionGranted: Boolean? = null

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
        if (!ensurePermissions()) return emptyList()
        val devices = navigator.mediaDevices.enumerateDevices().await<JsArray<MediaDeviceInfo>>()
        return devices.toList().filter { it.kind == type }
    }

    private suspend fun ensurePermissions(): Boolean {
        if (permissionGranted == true) return true
        // Calling getUserMedia is how you trigger the permission prompt.
        try {
            window.navigator.mediaDevices.getUserMedia(MediaStreamConstraints(audio = true.toJsBoolean())).await<MediaStream>()
            permissionGranted = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
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