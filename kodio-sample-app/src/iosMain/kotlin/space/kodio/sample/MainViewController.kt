package space.kodio.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

fun MainViewController(): UIViewController {
    val selfTest = NSProcessInfo.processInfo.environment["KODIO_SELFTEST"] != null
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
        if (selfTest) addWriter(StdoutLogWriter)
    }
    // Sample app: keep the screen on so long recordings and device tests don't
    // get interrupted by auto-lock.
    UIApplication.sharedApplication.idleTimerDisabled = true
    if (selfTest) launchDeviceSelfTest()
    return ComposeUIViewController { App() }
}
