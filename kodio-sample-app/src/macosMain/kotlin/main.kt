import androidx.compose.ui.window.Window
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter
import space.kodio.sample.App

fun main() {
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
    }

    NSApplication.sharedApplication()
    Window(title = "KodioApp"){
        App()
    }
    NSApp?.run()
}
