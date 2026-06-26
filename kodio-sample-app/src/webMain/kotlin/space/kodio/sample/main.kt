package space.kodio.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
    }

    ComposeViewport {
        App()
    }
}
