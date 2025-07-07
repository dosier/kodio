import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.lang.ref.WeakReference


/**
 * Android implementation for AudioSystem.
 * IMPORTANT: You must add the `RECORD_AUDIO` permission to your AndroidManifest.xml
 * and handle the runtime permission request in your application.
 */
actual val SystemAudioSystem: AudioSystem = AndroidAudioSystem

object AndroidAudioSystem : SystemAudioSystemImpl() {

    private lateinit var context : WeakReference<Context>
    private lateinit var activity : WeakReference<Activity>

    fun setApplicationContext(appContext: Context) {
        this.context = WeakReference(appContext.applicationContext)
    }

    fun setMicrophonePermissionRequestActivity(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.toInputDevice() }
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toOutputDevice() }
    }

    override fun createRecordingSession(device: AudioDevice.Input): RecordingSession =
        withMicrophonePermission { AndroidRecordingSession(requireContext(), device) }

    override fun createPlaybackSession(device: AudioDevice.Output): PlaybackSession =
        AndroidPlaybackSession(requireContext(), device)

    private fun<T> withMicrophonePermission(block: () -> T): T {
        return if (!hasMicrophonePermission(requireContext()))
            if (checkOrRequestMicrophonePermission(requireActivity(), requireContext()))
                block()
            else
                error("Failed to obtain microphone permission")
        else
            block()
    }

    private fun requireContext(): Context =
        requireNotNull(if (this::context.isInitialized) context.get() else null) {
            "AudioSystem not initialized. Call AndroidAudioSystem.setApplicationContext(context) first."
        }

    private fun requireActivity(): Activity =
        requireNotNull(if (this::activity.isInitialized) activity.get() else null) {
            "AudioSystem not initialized. Call AndroidAudioSystem.setMicrophonePermissionRequestActivity(activity) first."
        }
}

// --- Helper Extension Functions ---
