package space.kodio.core

import android.content.Context
import android.media.AudioManager
import space.kodio.core.security.AudioPermissionManager
import java.lang.ref.WeakReference

/**
 * Android implementation for AudioSystem.
 * IMPORTANT: You must add the `RECORD_AUDIO` permission to your AndroidManifest.xml
 * and handle the runtime permission request in your application.
 */
actual val SystemAudioSystem: AudioSystem = AndroidAudioSystem

object AndroidAudioSystem : SystemAudioSystemImpl() {

    private lateinit var context: WeakReference<Context>

    fun setApplicationContext(appContext: Context) {
        this.context = WeakReference(appContext.applicationContext)
    }

    override val permissionManager : AudioPermissionManager
        get() = AndroidAudioPermissionManager

    override suspend fun listInputDevices(): List<AudioDevice.Input> =
        requireContext().audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.toInputDevice() }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> =
        requireContext().audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toOutputDevice() }

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession =
        permissionManager.withMicrophonePermission { AndroidAudioRecordingSession(requireContext(), device) }

    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        AndroidAudioPlaybackSession(requireContext(), device)

    private fun requireContext(): Context =
        requireNotNull(if (this::context.isInitialized) context.get() else null) {
            "AndroidAudioSystem not initialized. Call AndroidAudioSystem.setApplicationContext(context) first."
        }
}
