package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.files.Path
import space.kodio.core.AudioFlow
import space.kodio.core.io.AudioSource
import space.kodio.core.io.collectAsSource

/**
 * Collects this flow and writes the PCM data to a file at [path].
 *
 * [format] selects the container and header (WAV, AIFF, or AU). The file
 * content reflects this flow's [AudioFlow.format], encoded in that container.
 */
suspend fun AudioFlow.writeToFile(format: AudioFileFormat, path: Path) {
    val source: AudioSource = collectAsSource()
    val writer = AudioFileWriter(format, path)
    writer.write(source)
}

/**
 * Collects this flow and writes the PCM data to [sink] in the chosen file
 * [format] (WAV, AIFF, or AU).
 */
suspend fun AudioFlow.writeToSink(format: AudioFileFormat, sink: Sink) {
    val source: AudioSource = collectAsSource()
    AudioFileCodecs.of(format).encode(source, sink)
}

/**
 * Collects this flow and returns an in-memory buffer containing the encoded
 * file bytes for [format] (WAV, AIFF, or AU).
 */
suspend fun AudioFlow.collectAsBuffer(
    format: AudioFileFormat,
): Buffer {
    val buffer = Buffer()
    writeToSink(format, buffer)
    return buffer
}
