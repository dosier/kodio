package space.kodio.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import space.kodio.core.Kodio
import space.kodio.core.initialize
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter
import space.kodio.core.onRequestPermissionsResult

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        Kodio.initialize(this)
        // `adb shell am start ... --es KODIO_SELFTEST 1` runs the scripted device check.
        val selfTest = intent?.getStringExtra("KODIO_SELFTEST") != null
        Kodio.configureLogging {
            minLevel = LogLevel.Debug
            addWriter(platformLogWriter())
            if (selfTest) addWriter(StdoutLogWriter)
        }
        // Sample app: keep the screen on while it is open (long recordings, device tests).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (selfTest && savedInstanceState == null) launchDeviceSelfTest()
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
        Kodio.onRequestPermissionsResult(requestCode, grantResults)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}