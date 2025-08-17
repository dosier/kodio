package space.kodio.compose.material3

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog

@Composable
fun ErrorDialog(error: Throwable) {
    var showDialog by remember(error) { mutableStateOf(false) }
    if (!showDialog) 
        return
    Dialog(onDismissRequest = { showDialog = false }) {
        Text(error.message?:"unknown error")
        HorizontalDivider()
        Column {
            Text(error.stackTraceToString())
        }
    }
}