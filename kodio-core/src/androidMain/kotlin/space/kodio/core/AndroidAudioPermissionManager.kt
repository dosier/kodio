package space.kodio.core

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import space.kodio.core.security.AudioPermissionDeniedException
import space.kodio.core.security.AudioPermissionManager
import java.lang.ref.WeakReference

object AndroidAudioPermissionManager : AudioPermissionManager() {

    private lateinit var activity: WeakReference<Activity>

    fun setMicrophonePermissionRequestActivity(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION_RECORD_AUDIO -> {
                setState(grantResults.getOrNull(0)?.toState()?:State.Unknown)
            }
        }
    }

    override suspend fun requestPermission() {
        ActivityCompat.requestPermissions(
            /* activity = */ requireActivity(),
            /* permissions = */ arrayOf(RECORD_AUDIO),
            /* requestCode = */ REQUEST_PERMISSION_RECORD_AUDIO
        )
    }

    override fun requestRedirectToSettings() {
        with(requireActivity()) {
            startActivity(
                Intent(
                    /* action = */ Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    /* uri = */ Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    override suspend fun checkState(): State =
        ContextCompat
            .checkSelfPermission(requireActivity().applicationContext, RECORD_AUDIO)
            .toState()

    private fun requireActivity(): Activity =
        requireNotNull(if (this::activity.isInitialized) activity.get() else null) {
            "AndroidAudioPermissionManager not initialized. Call AndroidAudioSystem.permissionManager.setMicrophonePermissionRequestActivity(activity) first."
        }
}

private const val REQUEST_PERMISSION_RECORD_AUDIO = 1001

private fun Int.toState(): AudioPermissionManager.State {
    return when(this) {
        PERMISSION_GRANTED -> AudioPermissionManager.State.Granted
        PERMISSION_DENIED -> AudioPermissionManager.State.Denied
        else -> AudioPermissionManager.State.Unknown
    }
}