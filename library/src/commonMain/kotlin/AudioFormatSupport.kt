/**
 * Represents the knowability of a device's supported audio formats.
 */
sealed interface AudioFormatSupport {

    val defaultFormat: AudioFormat

    /**
     * Represents the case where the platform can provide a definitive list of
     * supported formats and a default format. (e.g., JVM)
     */
    data class Known(
        val supportedFormats: List<AudioFormat>,
        override val defaultFormat: AudioFormat
    ) : AudioFormatSupport

    /**
     * Represents the case where the platform cannot determine the specific supported
     * formats. The library should rely on common standards. (e.g., JavaScript)
     */
    data object Unknown : AudioFormatSupport {
        override val defaultFormat: AudioFormat = AudioFormat.DEFAULT
    }
}