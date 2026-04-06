package space.kodio.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit

fun main() {
    // Enable debug logging for SLF4J simple logger
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    System.setProperty("org.slf4j.simpleLogger.log.RecorderState", "debug")
    
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