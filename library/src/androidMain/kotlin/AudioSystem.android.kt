import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private val _permissionState = MutableStateFlow(AndroidAudioPermissionState.Unknown)
    private val permissionState = _permissionState.asStateFlow()

    fun setApplicationContext(appContext: Context) {
        this.context = WeakReference(appContext.applicationContext)
    }

    fun setMicrophonePermissionRequestActivity(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        println("onRequestPermissionsResult($requestCode, ${grantResults.contentToString()})")
        when(requestCode) {
            REQUEST_PERMISSION_RECORD_AUDIO -> {
                _permissionState.value = when(grantResults.getOrNull(0)) {
                    PackageManager.PERMISSION_GRANTED -> AndroidAudioPermissionState.Granted
                    PackageManager.PERMISSION_DENIED -> AndroidAudioPermissionState.Denied
                    else -> AndroidAudioPermissionState.Unknown
                }
            }
        }
    }

    override suspend fun listInputDevices(): List<AudioDevice.Input> {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.toInputDevice() }
    }

    override suspend fun listOutputDevices(): List<AudioDevice.Output> {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toOutputDevice() }
    }

    override suspend fun createRecordingSession(device: AudioDevice.Input): AudioRecordingSession {
        return withMicrophonePermission { AndroidAudioRecordingSession(requireContext(), device) }
    }

    override suspend fun createPlaybackSession(device: AudioDevice.Output): AudioPlaybackSession =
        AndroidAudioPlaybackSession(requireContext(), device)

    private suspend fun<T> withMicrophonePermission(block: () -> T): T {
        val context = requireContext()
        return if (!hasMicrophonePermission(context)) {
            val activity = requireActivity()
            _permissionState.value = AndroidAudioPermissionState.Requesting
            coroutineScope {
                launch {
                    activity.requestPermission(RECORD_AUDIO, REQUEST_PERMISSION_RECORD_AUDIO)
                }
                launch {
                    permissionState.first { it != AndroidAudioPermissionState.Requesting }
                }
            }
            if (hasMicrophonePermission(context))
                block()
            else
                throw AudioPermissionDeniedException
        } else
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
