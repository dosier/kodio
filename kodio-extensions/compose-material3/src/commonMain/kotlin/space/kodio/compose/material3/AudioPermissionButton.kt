package space.kodio.compose.material3

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import space.kodio.compose.KodioIcons
import space.kodio.core.SystemAudioSystem
import space.kodio.core.security.AudioPermissionManager

@Composable
fun AudioPermissionButton(
    permissionState: AudioPermissionManager.State,
    icons: KodioIcons,
) {
    val scope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    AnimatedContent(permissionState) { permissionState ->
        IconButton(
            onClick = {
                when (permissionState) {
                    AudioPermissionManager.State.Unknown -> {
                        scope.launch {
                            SystemAudioSystem.permissionManager.requestPermission()
                            when(SystemAudioSystem.permissionManager.refreshState()) {
                                AudioPermissionManager.State.Denied -> showPermissionDialog = true
                                else -> Unit
                            }
                        }
                    }
                    else -> Unit
                }
            },
            enabled = permissionState != AudioPermissionManager.State.Requesting
        ) {
            when (permissionState) {
                AudioPermissionManager.State.Unknown -> {
                    Icon(icons.micIcon, "?")
                }
                AudioPermissionManager.State.Requesting -> {
                    CircularProgressIndicator()
                }
                AudioPermissionManager.State.Granted -> {
                    Icon(icons.checkIcon, "granted")
                }
                AudioPermissionManager.State.Denied -> {
                    Icon(icons.warningIcon, "denied")
                }
            }
        }
    }
    if (showPermissionDialog) {
        Dialog(onDismissRequest = { showPermissionDialog = false }) {
            Text("Missing microphone permissions")
            HorizontalDivider()
            TextButton(onClick = SystemAudioSystem.permissionManager::requestRedirectToSettings) {
                Text("Open settings")
            }
        }
    }
}