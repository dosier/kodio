package space.kodio.sample

import androidx.compose.ui.Modifier

/**
 * Modifier that enables receiving audio file drops from the OS.
 * On desktop, this handles drag-and-drop from the file manager.
 * On other platforms, this is a no-op (use the file picker instead).
 *
 * @param onDragStateChange called with `true` when a drag enters and `false` when it leaves or drops
 * @param onDrop called with the file name and raw bytes when a WAV file is dropped
 */
expect fun Modifier.audioFileDropTarget(
    onDragStateChange: (isDragging: Boolean) -> Unit,
    onDrop: (name: String, bytes: ByteArray) -> Unit
): Modifier
