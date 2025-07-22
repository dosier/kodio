package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.files.Path
import space.kodio.core.AudioFlow
import space.kodio.core.io.AudioSource
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.files.wav.writeWav

suspend fun AudioFlow.writeToFile(format: AudioFileFormat, path: Path) {
    val source: AudioSource = collectAsSource()
    val writer = AudioFileWriter(format, path)
    writer.write(source)
}

suspend fun AudioFlow.writeToSink(format: AudioFileFormat, sink: Sink) {
    val source: AudioSource = collectAsSource()
    when (format) {
        AudioFileFormat.Wav -> writeWav(source, sink)
    }
}

suspend fun AudioFlow.collectAsBuffer(
    format: AudioFileFormat,
): Buffer {
    val buffer = Buffer()
    writeToSink(format, buffer)
    return buffer
}
