package space.kodio.core.io.files

/**
 * Represents the file format to write.
 * This could be extended with other formats like AIFF, FLAC, etc.
 */
sealed class AudioFileFormat(
    val extension: String
) {
    data object Wav : AudioFileFormat("wav")
}