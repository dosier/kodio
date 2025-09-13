package space.kodio.sample

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.sink
import kotlinx.io.buffered
import space.kodio.core.AudioFlow
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToSink

actual suspend fun saveWavFile(audioDataFlow: AudioFlow) {
    val file = FileKit.openFileSaver(
        suggestedName = "kodio-test",
        extension = "wav",
    )
    if (file == null)
        return
    val sink = file.sink(append = false).buffered()
    sink.use {

        audioDataFlow.writeToSink(AudioFileFormat.Wav, it)
    }
}