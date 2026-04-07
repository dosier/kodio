package space.kodio.core.io.files

/**
 * Represents a supported audio container format for reading and writing.
 */
sealed class AudioFileFormat(
    val extension: String
) {
    data object Wav : AudioFileFormat("wav")
    data object Aiff : AudioFileFormat("aiff")
    data object Au : AudioFileFormat("au")

    companion object {
        val entries: List<AudioFileFormat> = listOf(Wav, Aiff, Au)
    }
}