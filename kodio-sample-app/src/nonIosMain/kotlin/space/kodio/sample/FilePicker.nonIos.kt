package space.kodio.sample

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker

actual suspend fun pickFile(extensions: List<String>): PlatformFile? {
    return FileKit.openFilePicker(type = FileKitType.File(extensions = extensions))
}
