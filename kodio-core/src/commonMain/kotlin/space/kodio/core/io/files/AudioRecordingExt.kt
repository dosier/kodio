package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import space.kodio.core.AudioRecording
import space.kodio.core.io.files.wav.readWav

/**
 * Decodes an audio file from raw bytes (e.g. Compose resources via `Res.readBytes()`,
 * network responses, or any in-memory representation).
 *
 * ```kotlin
 * val recording = AudioRecording.fromBytes(Res.readBytes("files/notification.wav"))
 * ```
 *
 * @param bytes The complete audio file bytes, including headers.
 * @param fileFormat The container format of the data (default: WAV).
 */
fun AudioRecording.Companion.fromBytes(
    bytes: ByteArray,
    fileFormat: AudioFileFormat = AudioFileFormat.Wav
): AudioRecording {
    val source = Buffer().apply { write(bytes) }
    return fromSource(source, fileFormat)
}

/**
 * Decodes an audio file from a [Source] stream (e.g. Android `ContentResolver`,
 * platform input streams, or any `kotlinx.io.Source`).
 *
 * ```kotlin
 * val source = contentResolver.openInputStream(uri)!!.asSource().buffered()
 * val recording = AudioRecording.fromSource(source)
 * ```
 *
 * @param source The source to read from. Will be consumed but not closed.
 * @param fileFormat The container format of the data (default: WAV).
 */
fun AudioRecording.Companion.fromSource(
    source: Source,
    fileFormat: AudioFileFormat = AudioFileFormat.Wav
): AudioRecording {
    val audioSource = when (fileFormat) {
        is AudioFileFormat.Wav -> readWav(source)
    }
    val pcmBytes = audioSource.source.readByteArray()
    return AudioRecording.fromOwnedChunks(
        format = audioSource.format,
        chunks = listOf(pcmBytes)
    )
}

/**
 * Reads an audio file from the filesystem. The format is auto-detected from
 * the file extension.
 *
 * ```kotlin
 * val recording = AudioRecording.fromFile(Path("recording.wav"))
 * ```
 *
 * @param path The file path to read from.
 * @param fileSystem The file system to use (default: [SystemFileSystem]).
 */
fun AudioRecording.Companion.fromFile(
    path: Path,
    fileSystem: FileSystem = SystemFileSystem
): AudioRecording = AudioFileReader(path, fileSystem).read()
