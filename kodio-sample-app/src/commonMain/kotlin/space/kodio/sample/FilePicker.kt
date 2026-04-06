package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

/**
 * Platform-safe file picker. On iOS, uses a custom UIDocumentPickerViewController
 * to work around a FileKit bug where the delegate callback can fire twice,
 * crashing with "Already resumed".
 */
expect suspend fun pickFile(extensions: List<String>): PlatformFile?
