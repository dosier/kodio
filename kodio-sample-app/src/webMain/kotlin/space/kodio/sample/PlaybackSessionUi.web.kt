package space.kodio.sample

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.io.readByteArray
import space.kodio.core.AudioFlow
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.collectAsBuffer

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
actual suspend fun saveWavFile(audioDataFlow: AudioFlow) {
    val bytes = audioDataFlow.collectAsBuffer(AudioFileFormat.Wav)
    FileKit.download(
        bytes = bytes.readByteArray(),
        fileName = "kodio-sample.wav"
    )
}