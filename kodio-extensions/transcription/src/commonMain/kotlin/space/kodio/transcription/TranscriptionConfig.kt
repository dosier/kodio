package space.kodio.transcription

/**
 * Configuration for transcription operations.
 *
 * @property language The language code for transcription (e.g., "en-US", "es", "fr")
 */
data class TranscriptionConfig(
    val language: String = "en-US",
) {
    companion object {
        /**
         * Default configuration with English language.
         */
        val Default = TranscriptionConfig()
    }
}

/**
 * Supported transcription providers.
 */
enum class TranscriptionProvider {
    /** OpenAI Whisper API (chunked transcription) */
    OPENAI_WHISPER
}
