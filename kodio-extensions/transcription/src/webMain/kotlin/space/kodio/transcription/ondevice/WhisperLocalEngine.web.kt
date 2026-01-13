package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * Web implementation of local Whisper engine.
 * 
 * TODO: Implement using whisper.wasm or transformers.js.
 */
actual object WhisperLocalEngine {
    
    actual fun create(model: WhisperModel, modelPath: String?): TranscriptionEngine = 
        WebWhisperLocalEngine(model, modelPath)
    
    // WebAssembly Whisper is possible but complex
    actual val isSupported: Boolean = false
    
    actual suspend fun downloadModel(model: WhisperModel, onProgress: ((Float) -> Unit)?): Boolean {
        // TODO: Download WASM model
        return false
    }
    
    actual fun isModelDownloaded(model: WhisperModel): Boolean {
        // TODO: Check IndexedDB for cached model
        return false
    }
}

private class WebWhisperLocalEngine(
    private val model: WhisperModel,
    private val modelPath: String?
) : TranscriptionEngine {
    
    override val provider = TranscriptionProvider.WHISPER_LOCAL
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "Local Whisper inference via WebAssembly is not yet implemented. " +
                     "Please use OpenAIWhisperEngine for cloud-based Whisper transcription.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // TODO: Release WASM resources
    }
}

