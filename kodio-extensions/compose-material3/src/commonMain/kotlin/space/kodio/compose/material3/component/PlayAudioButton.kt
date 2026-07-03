package space.kodio.compose.material3.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kodio.compose.PlayerState
import space.kodio.compose.material3.icons.Stop
import space.kodio.compose.rememberPlayerState
import space.kodio.core.AudioRecording

/**
 * Material 3 play button with pause, restart, and error handling for an [AudioRecording].
 */
@Composable
fun PlayAudioButton(
    recording: AudioRecording,
    modifier: Modifier = Modifier,
    state: PlayerState = rememberPlayerState(recording),
    showWaveform: Boolean = true,
) {
    val error = state.error

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = { state.toggle() },
            enabled = state.isReady && !state.isLoading,
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                state.isPlaying -> {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Pause playback",
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                    )
                }
            }
        }

        if (state.isPlaying || state.isPaused || state.isFinished) {
            IconButton(onClick = { state.stop() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart playback",
                )
            }
        }
    }

    if (error != null) {
        ErrorDialog(error = error, onDismiss = { state.clearError() })
    }
}
