package space.kodio.core.io.files

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import space.kodio.core.AudioRecording
import space.kodio.core.io.files.wav.readWav

/**
 * Reads an audio file from disk and returns an [AudioRecording].
 *
 * The file format is detected from the file extension. Currently only WAV is supported.
 *
 * @param path The file path to read from.
 * @param fileSystem The file system to use (default: [SystemFileSystem]).
 */
class AudioFileReader(
    private val path: Path,
    private val fileSystem: FileSystem = SystemFileSystem
) {

    /**
     * Reads the audio file and returns an [AudioRecording].
     *
     * @throws AudioFileReadError.InvalidFile if the file is not a valid audio file.
     * @throws AudioFileReadError.UnsupportedFormat if the audio encoding is not supported.
     * @throws AudioFileReadError.IO if a filesystem-level error occurs.
     */
    fun read(): AudioRecording {
        val format = detectFormat(path)

        val audioSource = try {
            fileSystem.source(path).buffered().use { source ->
                when (format) {
                    is AudioFileFormat.Wav -> readWav(source)
                }
            }
        } catch (e: AudioFileReadError) {
            throw e
        } catch (e: Exception) {
            throw AudioFileReadError.IO(e)
        }

        val pcmBytes = audioSource.source.readByteArray()
        return AudioRecording.fromOwnedChunks(
            format = audioSource.format,
            chunks = listOf(pcmBytes)
        )
    }

    companion object {
        internal fun detectFormat(path: Path): AudioFileFormat {
            val ext = path.toString().substringAfterLast('.', "").lowercase()
            return when (ext) {
                "wav", "wave" -> AudioFileFormat.Wav
                else -> throw AudioFileReadError.UnsupportedFormat(
                    "Unsupported file extension: '$ext'"
                )
            }
        }
    }
}
