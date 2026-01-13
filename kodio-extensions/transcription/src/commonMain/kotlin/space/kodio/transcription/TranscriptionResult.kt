package space.kodio.transcription

import kotlin.time.Duration

/**
 * Represents the result of a transcription operation.
 * 
 * Results are emitted as a stream during real-time transcription:
 * - [Partial] results are emitted as speech is being recognized (interim results)
 * - [Final] results are emitted when a segment of speech is finalized
 * - [Error] results indicate a problem during transcription
 */
sealed interface TranscriptionResult {
    
    /**
     * Partial/interim transcription result.
     * These are emitted during real-time transcription as the user speaks
     * and may change as more audio is processed.
     * 
     * @property text The current transcribed text (may change in subsequent updates)
     * @property confidence Confidence score from 0.0 to 1.0, if available
     */
    data class Partial(
        val text: String,
        val confidence: Float = 0f
    ) : TranscriptionResult
    
    /**
     * Final transcription result for a completed segment of speech.
     * Once emitted, this segment will not change.
     * 
     * @property text The finalized transcribed text
     * @property confidence Confidence score from 0.0 to 1.0
     * @property words Individual word-level details, if available
     * @property startTime Start time of this segment in the audio stream
     * @property endTime End time of this segment in the audio stream
     */
    data class Final(
        val text: String,
        val confidence: Float = 0f,
        val words: List<Word> = emptyList(),
        val startTime: Duration? = null,
        val endTime: Duration? = null
    ) : TranscriptionResult
    
    /**
     * Error during transcription.
     * 
     * @property message Human-readable error description
     * @property code Error code from the provider, if available
     * @property cause The underlying exception, if any
     * @property isRecoverable Whether the transcription can continue after this error
     */
    data class Error(
        val message: String,
        val code: String? = null,
        val cause: Throwable? = null,
        val isRecoverable: Boolean = false
    ) : TranscriptionResult
    
    /**
     * Word-level transcription detail.
     * 
     * @property text The word text
     * @property confidence Confidence score for this word
     * @property startTime Start time of this word in the audio
     * @property endTime End time of this word in the audio
     */
    data class Word(
        val text: String,
        val confidence: Float = 0f,
        val startTime: Duration? = null,
        val endTime: Duration? = null
    )
}

