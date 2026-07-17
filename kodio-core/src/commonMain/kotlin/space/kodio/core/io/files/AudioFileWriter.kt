package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import space.kodio.core.io.AudioSource

class AudioFileWriter(
    private val fileFormat: AudioFileFormat,
    private val path: Path,
    private val fileSystem: FileSystem = SystemFileSystem
) {

    /**
     * Writes the audio data from an [AudioSource] to a file in the specified format.
     *
     * @param audioDataSource The buffer containing the raw audio samples and format info.
     * @throws AudioFileWriteError.UnsupportedFormat if the format cannot be encoded.
     * @throws AudioFileWriteError.IO if a filesystem-level error occurs.
     */
    fun write(audioDataSource: AudioSource) {
        if (!AudioFileCodecs.of(fileFormat).canEncode) {
            throw AudioFileWriteError.UnsupportedFormat("Format $fileFormat does not support encoding.")
        }

        val audioFileBuffer = Buffer()
        try {
            AudioFileCodecs.of(fileFormat).encode(audioDataSource, audioFileBuffer)
        } catch (e: AudioFileWriteError) {
            throw e
        } catch (e: Exception) {
            throw AudioFileWriteError.IO(e)
        }

        try {
            fileSystem.sink(path).use { fileSink ->
                fileSink.write(audioFileBuffer, audioFileBuffer.size)
            }
        } catch (e: Exception) {
            throw AudioFileWriteError.IO(e)
        }
    }
}
