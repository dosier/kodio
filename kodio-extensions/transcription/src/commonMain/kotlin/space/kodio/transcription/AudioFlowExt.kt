package space.kodio.transcription

import kotlinx.coroutines.flow.Flow
import space.kodio.core.AudioFlow
import space.kodio.core.Recorder

/**
 * Extension functions for integrating transcription with Kodio's audio system.
 */

/**
 * Transcribes this [AudioFlow] using the specified [engine].
 * 
 * This is a convenience extension that makes it easy to pipe Kodio audio
 * directly into a transcription engine.
 * 
 * ## Example
 * ```kotlin
 * val engine = DeepgramEngine(apiKey = "...")
 * 
 * audioFlow.transcribe(engine)
 *     .collect { result ->
 *         when (result) {
 *             is TranscriptionResult.Partial -> updateUI(result.text)
 *             is TranscriptionResult.Final -> saveTranscript(result.text)
 *             is TranscriptionResult.Error -> showError(result.message)
 *         }
 *     }
 * ```
 * 
 * @param engine The transcription engine to use
 * @param config Configuration for transcription (language, model, etc.)
 * @return Flow of transcription results
 */
fun AudioFlow.transcribe(
    engine: TranscriptionEngine,
    config: TranscriptionConfig = TranscriptionConfig.Default
): Flow<TranscriptionResult> = engine.transcribe(this, config)

/**
 * Creates an [AudioFlow] from this recorder's live audio and transcribes it.
 * 
 * This is a convenience function for the common pattern of recording and
 * transcribing simultaneously.
 * 
 * ## Example
 * ```kotlin
 * val recorder = Kodio.recorder()
 * val engine = DeepgramEngine(apiKey = "...")
 * 
 * recorder.start()
 * 
 * recorder.transcribe(engine)?.collect { result ->
 *     println("Transcription: ${result}")
 * }
 * ```
 * 
 * @param engine The transcription engine to use
 * @param config Configuration for transcription
 * @return Flow of transcription results, or null if no audio flow is available
 */
fun Recorder.transcribe(
    engine: TranscriptionEngine,
    config: TranscriptionConfig = TranscriptionConfig.Default
): Flow<TranscriptionResult>? {
    val flow = liveAudioFlow ?: return null
    return AudioFlow(format, flow).transcribe(engine, config)
}

