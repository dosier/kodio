package space.kodio.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import space.kodio.core.AndroidAudioPermissionManager
import space.kodio.core.AndroidAudioSystem

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        AndroidAudioSystem.setApplicationContext(applicationContext)
        AndroidAudioPermissionManager.setMicrophonePermissionRequestActivity(this)
        setContent {
            App()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        AndroidAudioPermissionManager.onRequestPermissionsResult(requestCode, grantResults)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}