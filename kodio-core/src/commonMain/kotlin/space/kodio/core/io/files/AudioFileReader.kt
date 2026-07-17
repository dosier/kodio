package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import space.kodio.core.AudioRecording
import space.kodio.core.io.files.metadata.AudioFileMetadata

/**
 * Reads an audio file from disk and returns an [AudioRecording].
 *
 * Format is resolved via magic-byte sniffing with extension fallback
 * (see [resolveCodec]).
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
        validatePathExtension(path)
        return readBytes(path, fileSystem)
    }

    fun readMetadata(): AudioFileMetadata {
        validatePathExtension(path)
        return readMetadataBytes(path, fileSystem)
    }

    private fun validatePathExtension(path: Path) {
        val ext = extensionFromFileName(path.toString()) ?: return
        if (AudioFileCodecs.fromExtension(ext) == null) {
            throw AudioFileReadError.UnsupportedFormat("Unsupported file extension: '$ext'")
        }
    }

    companion object {
        /**
         * Reads an audio file from an in-memory byte array.
         *
         * Format is auto-detected from magic bytes; [fileName] provides an
         * extension fallback when magic is inconclusive.
         *
         * @throws AudioFileReadError.InvalidFile if the bytes are not a valid audio file.
         * @throws AudioFileReadError.UnsupportedFormat if the audio encoding is not supported.
         */
        fun read(bytes: ByteArray, fileName: String? = null): AudioRecording =
            decodeBytes(bytes, fileName, explicit = null)

        /**
         * Reads metadata from an in-memory byte array without decoding PCM.
         */
        fun readMetadata(bytes: ByteArray, fileName: String? = null): AudioFileMetadata =
            metadataFromBytes(bytes, fileName, explicit = null)

        internal fun readBytes(path: Path, fileSystem: FileSystem): AudioRecording {
            return try {
                fileSystem.source(path).buffered().use { source ->
                    decodeBytes(source.readByteArray(), path.toString(), explicit = null)
                }
            } catch (e: AudioFileReadError) {
                throw e
            } catch (e: Exception) {
                throw AudioFileReadError.IO(e)
            }
        }

        internal fun readMetadataBytes(path: Path, fileSystem: FileSystem): AudioFileMetadata {
            return try {
                fileSystem.source(path).buffered().use { source ->
                    metadataFromBytes(source.readByteArray(), path.toString(), explicit = null)
                }
            } catch (e: AudioFileReadError) {
                throw e
            } catch (e: Exception) {
                throw AudioFileReadError.IO(e)
            }
        }

        internal fun decodeBytes(
            bytes: ByteArray,
            fileName: String?,
            explicit: AudioFileFormat?,
        ): AudioRecording {
            val codec = resolveCodec(probePrefix(bytes), fileName, explicit)
            val source = Buffer().apply { write(bytes) }
            val audioSource = try {
                codec.decode(source)
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

        internal fun metadataFromBytes(
            bytes: ByteArray,
            fileName: String?,
            explicit: AudioFileFormat?,
        ): AudioFileMetadata {
            val codec = resolveCodec(probePrefix(bytes), fileName, explicit)
            val source = Buffer().apply { write(bytes) }
            return try {
                codec.readMetadata(source)
            } catch (e: AudioFileReadError) {
                throw e
            } catch (e: Exception) {
                throw AudioFileReadError.IO(e)
            }
        }

        internal fun decodeSource(
            source: Source,
            fileName: String?,
            explicit: AudioFileFormat?,
        ): AudioRecording = decodeBytes(source.readByteArray(), fileName, explicit)

        internal fun metadataFromSource(
            source: Source,
            fileName: String?,
            explicit: AudioFileFormat?,
        ): AudioFileMetadata = metadataFromBytes(source.readByteArray(), fileName, explicit)
    }
}
