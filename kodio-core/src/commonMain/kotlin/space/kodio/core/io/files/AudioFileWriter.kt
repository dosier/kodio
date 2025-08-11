package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.wav.writeWav

class AudioFileWriter(
    private val fileFormat: AudioFileFormat,
    private val path: Path,
    private val fileSystem: FileSystem = SystemFileSystem
) {

    /**
     * Writes the audio data from an AudioBuffer to a file in the specified format.
     *
     * @param audioDataSource The buffer containing the raw audio samples and format info.
     * @throws Exception if an unsupported format is provided or an I/O error occurs.
     * In a more robust application, this would return a Result<Unit, AudioWriteError>.
     */
    fun write(audioDataSource: AudioSource) {
        val audioFileBuffer = Buffer()
        when (fileFormat) {
            is AudioFileFormat.Wav -> writeWav(audioDataSource, audioFileBuffer)
        }
        // --- Write the completed buffer to the actual file on disk ---
        try {
            fileSystem.sink(path).use { fileSink ->
                fileSink.write(audioFileBuffer, audioFileBuffer.size)
            }
        } catch (e: Exception) {
            // Wrap any file system exception in our custom error type
            throw AudioFileWriteError.IO(e)
        }
    }
}