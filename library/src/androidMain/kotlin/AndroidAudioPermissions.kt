import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun checkOrRequestMicrophonePermission(activity: Activity, context: Context): Boolean  {
    return if (!hasMicrophonePermission(context)) {
        ActivityCompat.requestPermissions(activity, arrayOf(RECORD_AUDIO), 1001)
        hasMicrophonePermission(context)
    } else true
}

internal fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PERMISSION_GRANTED