package space.kodio.compose.material3.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import space.kodio.core.AudioError
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ErrorDialogTest {

    @Test
    fun errorDialogShowsMessage() = runComposeUiTest {
        val error = AudioError.NoRecordingData()

        setContent {
            ErrorDialog(error = error, onDismiss = {})
        }

        waitForIdle()

        onNodeWithText("No recording data available").assertIsDisplayed()
        onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun errorDialogDismissInvokesCallback() = runComposeUiTest {
        var dismissed = false

        setContent {
            ErrorDialog(error = AudioError.NoRecordingData()) {
                dismissed = true
            }
        }

        waitForIdle()

        onNodeWithText("Dismiss").performClick()
        waitForIdle()

        assertTrue(dismissed)
    }
}
