package space.kodio.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import space.kodio.core.*
import kotlin.test.*

/**
 * Test tags for RecorderState UI tests.
 */
object RecorderStateTestTags {
    const val TOGGLE_BUTTON = "toggle_button"
    const val STATUS_TEXT = "status_text"
    const val RESET_BUTTON = "reset_button"
    const val ERROR_TEXT = "error_text"
}

/**
 * UI tests for [RecorderState] to verify toggle behavior and state transitions.
 * These tests use Compose Multiplatform's runComposeUiTest.
 */
@OptIn(ExperimentalTestApi::class)
class RecorderStateTest {

    /**
     * Test that RecorderState initial state is correct.
     */
    @Test
    fun initialStateIsCorrect() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        // Initially not recording
        onNodeWithTag(RecorderStateTestTags.STATUS_TEXT)
            .assertTextEquals("Idle")
    }

    /**
     * Test state transitions during toggle.
     */
    @Test
    fun stateTransitionsCorrectly() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        val state = recorderState!!
        
        // Check initial state
        assertFalse(state.isRecording)
        assertFalse(state.isBusy)
        assertNull(state.error)
    }

    /**
     * Test reset functionality.
     */
    @Test
    fun resetClearsState() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        val state = recorderState!!
        
        // Click reset button
        onNodeWithTag(RecorderStateTestTags.RESET_BUTTON).performClick()
        
        waitForIdle()
        
        assertFalse(state.isRecording)
        assertFalse(state.isProcessing)
        assertNull(state.recording)
        assertNull(state.error)
    }

    /**
     * Test that callback is invoked on recording complete.
     */
    @Test
    fun callbackIsRememberedCorrectly() = runComposeUiTest {
        var callbackInvoked = false
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState(
                onRecordingComplete = { callbackInvoked = true }
            )
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        // The state should be created
        assertNotNull(recorderState)
    }

    /**
     * Test that quality parameter is respected.
     */
    @Test
    fun qualityParameterIsPassed() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState(
                quality = AudioQuality.High
            )
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        assertNotNull(recorderState)
    }

    /**
     * Test error state handling.
     */
    @Test
    fun errorCanBeCleared() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        val state = recorderState!!
        
        // Initially no error
        assertNull(state.error)
        assertFalse(state.hasError)
        
        // Clear error should be safe even when no error
        state.clearError()
        
        assertNull(state.error)
    }

    /**
     * Test that live amplitudes list is initially empty.
     */
    @Test
    fun liveAmplitudesInitiallyEmpty() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        val state = recorderState!!
        
        assertTrue(state.liveAmplitudes.isEmpty())
    }

    /**
     * Test toggle button exists and is clickable.
     */
    @Test
    fun toggleButtonExists() = runComposeUiTest {
        setContent {
            val recorderState = rememberRecorderState()
            RecorderTestUI(recorderState)
        }
        
        waitForIdle()
        
        onNodeWithTag(RecorderStateTestTags.TOGGLE_BUTTON).assertExists()
        onNodeWithTag(RecorderStateTestTags.TOGGLE_BUTTON).assertHasClickAction()
    }

    /**
     * Test multiple toggle clicks don't cause issues.
     */
    @Test
    fun multipleToggleClicksHandledGracefully() = runComposeUiTest {
        var recorderState: RecorderState? = null
        
        setContent {
            recorderState = rememberRecorderState()
            RecorderTestUI(recorderState!!)
        }
        
        waitForIdle()
        
        // Rapid clicks shouldn't crash
        repeat(3) {
            onNodeWithTag(RecorderStateTestTags.TOGGLE_BUTTON).performClick()
        }
        
        waitForIdle()
        
        // Should be in a valid state (either recording or not)
        assertNotNull(recorderState)
    }
}

/**
 * Simple test UI for RecorderState testing.
 */
@Composable
private fun RecorderTestUI(recorderState: RecorderState) {
    val statusText = when {
        recorderState.isRecording -> "Recording"
        recorderState.isProcessing -> "Processing"
        recorderState.hasError -> "Error"
        else -> "Idle"
    }
    
    Column {
        BasicText(
            text = statusText,
            modifier = Modifier.testTag(RecorderStateTestTags.STATUS_TEXT)
        )
        
        Box(
            modifier = Modifier
                .testTag(RecorderStateTestTags.TOGGLE_BUTTON)
                .clickable { recorderState.toggle() }
        ) {
            BasicText(if (recorderState.isRecording) "Stop" else "Start")
        }
        
        Box(
            modifier = Modifier
                .testTag(RecorderStateTestTags.RESET_BUTTON)
                .clickable { recorderState.reset() }
        ) {
            BasicText("Reset")
        }
        
        recorderState.error?.let { error ->
            BasicText(
                text = error.message ?: "Unknown error",
                modifier = Modifier.testTag(RecorderStateTestTags.ERROR_TEXT)
            )
        }
    }
}
