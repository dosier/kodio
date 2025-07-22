package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import space.kodio.core.Encoding
import space.kodio.core.io.AudioSource

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
            is AudioFileFormat.Wav -> {
                // Get properties from the audio format
                val format = audioDataSource.format
                val numChannels = format.channels.count
                val bitsPerSample = format.bitDepth.value
                val sampleRate = format.sampleRate

                // --- WAV Header Calculations ---

                // 1 for PCM. The official spec also defines 3 for IEEE Float.
                // We will only support PCM as defined in the user's Encoding class.
                val audioFormatCode = when (format.encoding) {
                    is Encoding.Pcm -> 1
                    // If the encoding was extended, you could add other cases here:
                    // is Encoding.IeeeFloat -> 3
                    else -> throw Exception(
                        AudioFileWriteError.UnsupportedFormatError(
                            "Unsupported encoding for WAV file: ${format.encoding}"
                        ).toString()
                    )
                }

                // The size of one "frame" of audio (all channels for one sample point)
                val blockAlign = numChannels * bitsPerSample / 8
                // The number of bytes per second
                val byteRate = sampleRate * blockAlign

                // Size of the actual audio data payload in bytes
                val subChunk2Size = audioDataSource.byteCount
                // Total file size minus the first 8 bytes ("RIFF" and the size field itself)
                val chunkSize = 36 + subChunk2Size

                // --- Write WAV Header to Buffer ---
                with(audioFileBuffer) {
                    // -- RIFF Chunk Descriptor --
                    writeString("RIFF") // ChunkID: Contains the letters "RIFF"
                    writeIntLe(chunkSize.toInt()) // ChunkSize: 36 + SubChunk2Size
                    writeString("WAVE") // Format: Contains the letters "WAVE"

                    // -- "fmt " Sub-Chunk --
                    writeString("fmt ") // Subchunk1ID: Contains "fmt " (note the space)
                    writeIntLe(16) // Subchunk1Size: 16 for PCM
                    writeShortLe(audioFormatCode.toShort()) // AudioFormat: 1 for PCM
                    writeShortLe(numChannels.toShort()) // NumChannels: Mono = 1, Stereo = 2
                    writeIntLe(sampleRate) // SampleRate: e.g., 44100, 48000
                    writeIntLe(byteRate) // ByteRate: SampleRate * NumChannels * BitsPerSample/8
                    writeShortLe(blockAlign.toShort()) // BlockAlign: NumChannels * BitsPerSample/8
                    writeShortLe(bitsPerSample.toShort()) // BitsPerSample: 8, 16, 24, etc.

                    // -- "data" Sub-Chunk --
                    writeString("data") // Subchunk2ID: Contains "data"
                    writeIntLe(subChunk2Size.toInt()) // Subchunk2Size: Size of the audio data
                }

                // --- Write Actual Audio Data ---
                audioFileBuffer.write(audioDataSource.source, audioDataSource.byteCount)
            }
        }

        // --- Write the completed buffer to the actual file on disk ---
        try {
            fileSystem.sink(path).use { fileSink ->
                fileSink.write(audioFileBuffer, audioFileBuffer.size)
            }
        } catch (e: Exception) {
            // Wrap any file system exception in our custom error type
            throw Exception(AudioFileWriteError.IOError(e).toString())
        }
    }
}