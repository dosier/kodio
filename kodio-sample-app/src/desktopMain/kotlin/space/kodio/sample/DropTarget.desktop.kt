package space.kodio.sample

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.audioFileDropTarget(
    onDragStateChange: (isDragging: Boolean) -> Unit,
    onDrop: (name: String, bytes: ByteArray) -> Unit
): Modifier = composed {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnDragState by rememberUpdatedState(onDragStateChange)

    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnDragState(false)
                val dragData = event.dragData()
                if (dragData is DragData.FilesList) {
                    val uris = dragData.readFiles()
                    val wavUri = uris.firstOrNull { uri ->
                        val lower = uri.lowercase()
                        lower.endsWith(".wav") || lower.endsWith(".wave")
                    }
                    if (wavUri != null) {
                        try {
                            val file = java.io.File(java.net.URI.create(wavUri))
                            currentOnDrop(file.name, file.readBytes())
                            return true
                        } catch (e: Exception) {
                            println("Drop error: ${e.message}")
                        }
                    }
                }
                return false
            }

            override fun onEntered(event: DragAndDropEvent) {
                currentOnDragState(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnDragState(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnDragState(false)
            }
        }
    }

    this.dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = target
    )
}
