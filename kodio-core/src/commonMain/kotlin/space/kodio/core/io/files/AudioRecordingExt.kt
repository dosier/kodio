package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import space.kodio.core.AudioRecording

/**
 * Decodes an audio file from raw bytes (e.g. Compose resources via `Res.readBytes()`,
 * network responses, or any in-memory representation).
 *
 * ```kotlin
 * val recording = AudioRecording.fromBytes(Res.readBytes("files/notification.wav"))
 * ```
 *
 * @param bytes The complete audio file bytes, including headers.
 * @param fileName Optional filename for extension-based format fallback.
 */
fun AudioRecording.Companion.fromBytes(
    bytes: ByteArray,
    fileName: String? = null,
): AudioRecording = AudioFileReader.decodeBytes(bytes, fileName, explicit = null)

/**
 * Decodes an audio file from raw bytes using an explicit container format.
 */
fun AudioRecording.Companion.fromBytes(
    bytes: ByteArray,
    fileFormat: AudioFileFormat,
): AudioRecording = AudioFileReader.decodeBytes(bytes, fileName = null, explicit = fileFormat)

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
 * @param fileName Optional filename for extension-based format fallback.
 */
fun AudioRecording.Companion.fromSource(
    source: Source,
    fileName: String? = null,
): AudioRecording = AudioFileReader.decodeSource(source, fileName, explicit = null)

/**
 * Decodes an audio file from a [Source] using an explicit container format.
 */
fun AudioRecording.Companion.fromSource(
    source: Source,
    fileFormat: AudioFileFormat,
): AudioRecording = AudioFileReader.decodeSource(source, fileName = null, explicit = fileFormat)

/**
 * Reads an audio file from the filesystem. The format is auto-detected from
 * magic bytes with extension fallback.
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
