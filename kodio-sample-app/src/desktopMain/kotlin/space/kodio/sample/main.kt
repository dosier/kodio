package space.kodio.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit

fun main() = application {
    FileKit.init(appId = "KodioApp")
    Window(
        onCloseRequest = ::exitApplication,
        title = "KodioApp",
    ) {
        App()
    }
}