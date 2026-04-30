package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun transcribeFile(
    file: PlatformFile,
    apiKey: String,
    onUploadProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)?,
): FileTranscriptionResult = withContext(Dispatchers.IO) {
    transcribeWhisperFileViaKtor(
        fileBytes = file.readBytes(),
        fileName = file.name,
        apiKey = apiKey,
        onUploadProgress = onUploadProgress,
    )
}
