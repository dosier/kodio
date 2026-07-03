package space.kodio.compose.material3.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import space.kodio.core.AudioError

/**
 * Displays an [AudioError] in a Material 3 alert dialog with a dismiss action.
 */
@Composable
fun ErrorDialog(error: AudioError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
            )
        },
        title = {
            Text("Audio error")
        },
        text = {
            Text(error.message ?: "An unknown audio error occurred.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    )
}
