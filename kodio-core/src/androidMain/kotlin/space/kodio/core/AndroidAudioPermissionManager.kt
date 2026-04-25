package space.kodio.core

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import space.kodio.core.security.AudioPermissionManager
import java.lang.ref.WeakReference

object AndroidAudioPermissionManager : AudioPermissionManager() {

    private lateinit var activity: WeakReference<Activity>

    // Coalesces concurrent requestPermission() callers into a single
    // ActivityCompat.requestPermissions invocation. All access is guarded by
    // the singleton's intrinsic monitor (synchronized(this)).
    private var inflight: CompletableDeferred<Unit>? = null

    fun setMicrophonePermissionRequestActivity(activity: Activity) {
        val previous = if (this::activity.isInitialized) this.activity.get() else null
        this.activity = WeakReference(activity)
        if (previous != null && previous !== activity) {
            // Old activity is gone; any pending request can no longer be answered through it.
            // Cancel waiters so they don't hang; new callers will start a fresh request.
            val toCancel = synchronized(this) {
                val current = inflight
                inflight = null
                current
            }
            toCancel?.cancel()
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_PERMISSION_RECORD_AUDIO) return
        setState(grantResults.getOrNull(0)?.toState() ?: State.Unknown)
        val toComplete = synchronized(this) {
            val current = inflight
            inflight = null
            current
        }
        toComplete?.complete(Unit)
    }

    override suspend fun requestPermission() {
        val deferred = synchronized(this) {
            inflight?.takeIf { !it.isCompleted } ?: CompletableDeferred<Unit>().also { fresh ->
                inflight = fresh
                ActivityCompat.requestPermissions(
                    /* activity = */ requireActivity(),
                    /* permissions = */ arrayOf(RECORD_AUDIO),
                    /* requestCode = */ REQUEST_PERMISSION_RECORD_AUDIO,
                )
            }
        }
        deferred.await()
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
