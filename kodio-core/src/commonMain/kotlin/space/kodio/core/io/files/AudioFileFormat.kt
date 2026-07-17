package space.kodio.core.io.files

import kotlinx.io.Source

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

        /** Magic-byte sniff; returns null if unrecognized. */
        fun detect(bytes: ByteArray): AudioFileFormat? =
            AudioFileCodecs.probe(probePrefix(bytes))?.format

        /** Magic-byte sniff; returns null if unrecognized. Consumes up to 64 bytes from [source]. */
        fun detect(source: Source): AudioFileFormat? {
            val prefix = readPrefix(source, 64)
            if (prefix.isEmpty()) return null
            return detect(prefix)
        }

        private fun readPrefix(source: Source, maxLength: Int): ByteArray {
            val bytes = ByteArray(maxLength)
            var count = 0
            while (count < maxLength) {
                try {
                    bytes[count] = source.readByte()
                    count++
                } catch (_: Exception) {
                    break
                }
            }
            return bytes.copyOf(count)
        }

        /** Extension lookup; null if unknown. */
        fun fromExtension(extension: String): AudioFileFormat? =
            AudioFileCodecs.fromExtension(extension)?.format
    }
}
