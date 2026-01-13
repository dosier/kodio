package space.kodio.transcription

/**
 * Configuration for transcription operations.
 * 
 * @property language The language code for transcription (e.g., "en-US", "es", "fr")
 * @property model The model to use for transcription (provider-specific)
 * @property interimResults Whether to emit partial/interim results during transcription
 * @property punctuation Whether to include punctuation in results
 * @property profanityFilter Whether to filter profanity from results
 * @property diarization Whether to enable speaker diarization (identifying different speakers)
 * @property keywords List of keywords/phrases to boost recognition for
 */
data class TranscriptionConfig(
    val language: String = "en-US",
    val model: String? = null,
    val interimResults: Boolean = true,
    val punctuation: Boolean = true,
    val profanityFilter: Boolean = false,
    val diarization: Boolean = false,
    val keywords: List<String> = emptyList()
) {
    companion object {
        /**
         * Default configuration with English language and interim results enabled.
         */
        val Default = TranscriptionConfig()
        
        /**
         * Configuration optimized for voice commands (no punctuation, no interim).
         */
        val VoiceCommand = TranscriptionConfig(
            interimResults = false,
            punctuation = false
        )
        
        /**
         * Configuration for meeting transcription with speaker identification.
         */
        val Meeting = TranscriptionConfig(
            diarization = true,
            punctuation = true
        )
    }
}

/**
 * Supported transcription providers.
 */
enum class TranscriptionProvider {
    /** OpenAI Whisper API (chunked transcription) */
    OPENAI_WHISPER
}

