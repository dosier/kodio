package space.kodio.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import kotlin.test.*

/**
 * Test tags for PlayerState UI tests.
 */
object PlayerStateTestTags {
    const val TOGGLE_BUTTON = "player_toggle_button"
    const val RESET_BUTTON = "player_reset_button"
    const val STOP_BUTTON = "player_stop_button"
    const val CLEAR_ERROR_BUTTON = "player_clear_error_button"
}

/**
 * UI tests for [PlayerState] to verify initial state and safe no-op operations
 * without real audio hardware or loaded recordings.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerStateTest {

    @Test
    fun initialStateIsCorrect() = runComposeUiTest {
        var playerState: PlayerState? = null

        setContent {
            playerState = rememberPlayerState()
            PlayerTestUI(playerState!!)
        }

        waitForIdle()

        val state = playerState!!
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
        assertFalse(state.hasRecording)
        assertNull(state.error)
    }

    @Test
    fun clearErrorIsSafe() = runComposeUiTest {
        var playerState: PlayerState? = null

        setContent {
            playerState = rememberPlayerState()
            PlayerTestUI(playerState!!)
        }

        waitForIdle()

        val state = playerState!!
        assertNull(state.error)

        onNodeWithTag(PlayerStateTestTags.CLEAR_ERROR_BUTTON).performClick()

        waitForIdle()

        assertNull(state.error)
    }

    @Test
    fun stopWhenIdleIsSafe() = runComposeUiTest {
        var playerState: PlayerState? = null

        setContent {
            playerState = rememberPlayerState()
            PlayerTestUI(playerState!!)
        }

        waitForIdle()

        val state = playerState!!
        assertFalse(state.isPlaying)

        onNodeWithTag(PlayerStateTestTags.STOP_BUTTON).performClick()

        waitForIdle()

        assertFalse(state.isPlaying)
    }

    @Test
    fun resetClearsState() = runComposeUiTest {
        var playerState: PlayerState? = null

        setContent {
            playerState = rememberPlayerState()
            PlayerTestUI(playerState!!)
        }

        waitForIdle()

        val state = playerState!!

        onNodeWithTag(PlayerStateTestTags.RESET_BUTTON).performClick()

        waitForIdle()

        assertNull(state.recording)
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
        assertFalse(state.hasRecording)
    }

    @Test
    fun toggleWithoutRecordingReturnsNull() = runComposeUiTest {
        var playerState: PlayerState? = null

        setContent {
            playerState = rememberPlayerState()
            PlayerTestUI(playerState!!)
        }

        waitForIdle()

        val state = playerState!!
        assertFalse(state.isPlaying)
        assertFalse(state.hasRecording)

        onNodeWithTag(PlayerStateTestTags.TOGGLE_BUTTON).performClick()

        waitForIdle()

        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
    }
}

/**
 * Simple test UI for PlayerState testing.
 */
@Composable
private fun PlayerTestUI(playerState: PlayerState) {
    Column {
        Box(
            modifier = Modifier
                .testTag(PlayerStateTestTags.TOGGLE_BUTTON)
                .clickable { playerState.toggle() }
        ) {
            BasicText(if (playerState.isPlaying) "Pause" else "Play")
        }

        Box(
            modifier = Modifier
                .testTag(PlayerStateTestTags.STOP_BUTTON)
                .clickable { playerState.stop() }
        ) {
            BasicText("Stop")
        }

        Box(
            modifier = Modifier
                .testTag(PlayerStateTestTags.RESET_BUTTON)
                .clickable { playerState.reset() }
        ) {
            BasicText("Reset")
        }

        Box(
            modifier = Modifier
                .testTag(PlayerStateTestTags.CLEAR_ERROR_BUTTON)
                .clickable { playerState.clearError() }
        ) {
            BasicText("Clear Error")
        }
    }
}
