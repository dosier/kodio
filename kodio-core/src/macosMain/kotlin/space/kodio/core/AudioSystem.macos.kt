package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreAudio.kAudioObjectPropertyScopeInput
import platform.CoreAudio.kAudioObjectPropertyScopeOutput
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager

/**
 * macOS implementation for [AudioSystem].
 *
 * Enumerates devices via CoreAudio HAL:
 *  - listInputDevices(): devices with any input channels
 *  - listOutputDevices(): devices with any output channels
 *
 * @see MacosAudioPlaybackSession
 * @see MacosAudioRecordingSession
 */
actual val SystemAudioSystem: AudioSystem = object : SystemAudioSystemImpl() {

    override val permissionManager: AudioPermissionManager
        get() = MacosAudioPermissionManager

    // ───────────────────────────────────────────────────────────────────────────
    // Public API
    // ───────────────────────────────────────────────────────────────────────────

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        // Listing itself doesn’t require mic permission on macOS,
        // but if your app policy wants a permission gate, keep it.
        return try {
            permissionManager.withMicrophonePermission {
                enumerateDevices(scope = kAudioObjectPropertyScopeInput)
                    .map {
                        AudioDevice.Input(
                            id = it.uid,
                            name = it.name,
                            formatSupport = it.formatSupport
                        )
                    }
            }
        } catch (_: AudioPermissionDeniedException) {
            emptyList()
        }
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        val outputs = enumerateDevices(scope = kAudioObjectPropertyScopeOutput)
        return outputs.map {
            AudioDevice.Output(
                id = it.uid,
                name = it.name,
                formatSupport = it.formatSupport
            )
        }
    }

    override suspend fun createRecordingSession(requestedDevice: AudioDevice.Input?): AudioRecordingSession =
        permissionManager.withMicrophonePermission { MacosAudioRecordingSession(requestedDevice) }

    @ExperimentalForeignApi
    override suspend fun createPlaybackSession(requestedDevice: AudioDevice.Output?): AudioPlaybackSession =
        MacosAudioPlaybackSession(requestedDevice)
}
