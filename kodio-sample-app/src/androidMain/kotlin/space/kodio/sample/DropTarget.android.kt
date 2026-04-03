package space.kodio.sample

import androidx.compose.ui.Modifier

actual fun Modifier.audioFileDropTarget(
    onDragStateChange: (isDragging: Boolean) -> Unit,
    onDrop: (name: String, bytes: ByteArray) -> Unit
): Modifier = this
