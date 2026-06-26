package space.kodio.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

fun main() {
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
    }

    application {
        FileKit.init(appId = "KodioApp")
        Window(
            onCloseRequest = ::exitApplication,
            title = "KodioApp",
        ) {
            App()
        }
    }
}
