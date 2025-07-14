import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal const val REQUEST_PERMISSION_RECORD_AUDIO = 1001

internal fun Activity.requestPermission(permissionName: String?, permissionRequestCode: Int) {
    ActivityCompat.requestPermissions(
        /* activity = */ this,
        /* permissions = */ arrayOf(permissionName),
        /* requestCode = */ permissionRequestCode
    )
}

internal fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PERMISSION_GRANTED