package space.kodio.compose.material3.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecordAudioButtonTest {

    @Test
    fun recordButtonComposes() = runComposeUiTest {
        setContent {
            RecordAudioButton()
        }

        waitForIdle()

        onRoot().assertExists()
    }
}
