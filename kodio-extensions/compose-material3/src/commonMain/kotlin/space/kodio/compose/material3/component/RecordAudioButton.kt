package space.kodio.compose.material3.component

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kodio.compose.AudioWaveform
import space.kodio.compose.RecorderState
import space.kodio.compose.WaveformColors
import space.kodio.compose.WaveformStyle
import space.kodio.compose.material3.icons.Mic
import space.kodio.compose.material3.icons.Stop
import space.kodio.compose.rememberRecorderState

/**
 * Material 3 record button with optional live waveform and permission handling.
 */
@Composable
fun RecordAudioButton(
    modifier: Modifier = Modifier,
    state: RecorderState = rememberRecorderState(),
    showWaveform: Boolean = true,
) {
    val error = state.error

    Row(
        modifier = modifier.animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.needsPermission) {
            AudioPermissionButton(state = state)
        } else {
            RecordToggleButton(state = state)
        }

        if (showWaveform && (state.isRecording || state.liveAmplitudes.isNotEmpty())) {
            AudioWaveform(
                amplitudes = state.liveAmplitudes,
                style = WaveformStyle.Mirrored(),
                colors = WaveformColors.solidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(40.dp)
                    .width(120.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }

    if (error != null) {
        ErrorDialog(error = error, onDismiss = { state.clearError() })
    }
}

@Composable
private fun RecordToggleButton(state: RecorderState) {
    FilledIconButton(
        onClick = { state.toggle() },
        enabled = !state.isProcessing,
    ) {
        when {
            state.isProcessing -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            state.isRecording -> {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop recording",
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Start recording",
                )
            }
        }
    }
}
