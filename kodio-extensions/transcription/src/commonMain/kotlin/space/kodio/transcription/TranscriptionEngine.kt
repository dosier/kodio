package space.kodio.transcription

import kotlinx.coroutines.flow.Flow
import space.kodio.core.AudioFlow

/**
 * Core interface for speech-to-text transcription engines.
 * 
 * Implementations can be cloud-based (Deepgram, AssemblyAI, OpenAI) or
 * on-device (platform native APIs, local Whisper models).
 * 
 * ## Usage Example
 * ```kotlin
 * val engine = DeepgramEngine(apiKey = "your-api-key")
 * 
 * recorder.liveAudioFlow?.let { flow ->
 *     val audioFlow = AudioFlow(recorder.format, flow)
 *     engine.transcribe(audioFlow, TranscriptionConfig.Default)
 *         .collect { result ->
 *             when (result) {
 *                 is TranscriptionResult.Partial -> println("Hearing: ${result.text}")
 *                 is TranscriptionResult.Final -> println("Final: ${result.text}")
 *                 is TranscriptionResult.Error -> println("Error: ${result.message}")
 *             }
 *         }
 * }
 * ```
 */
interface TranscriptionEngine {
    
    /**
     * The provider type for this engine.
     */
    val provider: TranscriptionProvider
    
    /**
     * Whether this engine is currently available for use.
     * 
     * For cloud engines, this typically returns true if configured with valid credentials.
     * For on-device engines, this checks if the required models/APIs are available.
     */
    val isAvailable: Boolean
    
    /**
     * Transcribes audio from the given [AudioFlow] in real-time.
     * 
     * Returns a [Flow] of [TranscriptionResult] that emits:
     * - [TranscriptionResult.Partial] for interim results (if enabled in config)
     * - [TranscriptionResult.Final] for finalized transcription segments
     * - [TranscriptionResult.Error] for any errors during transcription
     * 
     * The flow completes when the input [audioFlow] completes or when an
     * unrecoverable error occurs.
     * 
     * @param audioFlow The audio data to transcribe
     * @param config Configuration for the transcription
     * @return Flow of transcription results
     */
    fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig = TranscriptionConfig.Default
    ): Flow<TranscriptionResult>
    
    /**
     * Releases any resources held by this engine.
     * 
     * After calling this method, the engine should not be used for further transcription.
     * For cloud engines, this closes any open connections.
     * For on-device engines, this releases loaded models.
     */
    fun release()
}

/**
 * Exception thrown when transcription operations fail.
 */
class TranscriptionException(
    message: String,
    val code: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

