import androidx.compose.ui.window.Window
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import space.kodio.sample.App

fun main() {
    NSApplication.sharedApplication()
    Window(title = "KodioApp"){
        App()
    }
    NSApp?.run()
}