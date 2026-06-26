package space.kodio.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

fun MainViewController(): UIViewController {
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
    }
    return ComposeUIViewController { App() }
}
