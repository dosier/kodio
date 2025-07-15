package gg.kodio.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import gg.kodio.core.AndroidAudioSystem

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidAudioSystem.setApplicationContext(applicationContext)
        AndroidAudioSystem.setMicrophonePermissionRequestActivity(this)
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
        AndroidAudioSystem.onRequestPermissionsResult(requestCode, grantResults)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}