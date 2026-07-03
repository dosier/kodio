package space.kodio.compose.material3.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kodio.compose.RecorderState
import space.kodio.compose.material3.icons.Mic
import space.kodio.core.security.AudioPermissionManager

/**
 * Material 3 icon button that reflects microphone permission state and requests access when needed.
 */
@Composable
fun AudioPermissionButton(state: RecorderState, modifier: Modifier = Modifier) {
    val permissionState = state.permissionState

    FilledIconButton(
        onClick = {
            when (permissionState) {
                AudioPermissionManager.State.Unknown,
                AudioPermissionManager.State.Denied -> state.requestPermission()
                else -> Unit
            }
        },
        modifier = modifier,
        enabled = permissionState != AudioPermissionManager.State.Requesting,
    ) {
        when (permissionState) {
            AudioPermissionManager.State.Unknown -> {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Grant microphone permission",
                )
            }
            AudioPermissionManager.State.Requesting -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            AudioPermissionManager.State.Granted -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Microphone permission granted",
                )
            }
            AudioPermissionManager.State.Denied -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Microphone permission denied",
                )
            }
        }
    }
}
