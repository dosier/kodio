package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * JVM implementation of local Whisper engine.
 * 
 * TODO: Implement using whisper.cpp JNI bindings for on-device inference.
 * For now, this returns a stub directing users to cloud engines.
 */
actual object WhisperLocalEngine {
    
    actual fun create(model: WhisperModel, modelPath: String?): TranscriptionEngine = 
        JvmWhisperLocalEngine(model, modelPath)
    
    actual val isSupported: Boolean = false
    
    actual suspend fun downloadModel(model: WhisperModel, onProgress: ((Float) -> Unit)?): Boolean {
        // TODO: Implement model download
        return false
    }
    
    actual fun isModelDownloaded(model: WhisperModel): Boolean {
        // TODO: Check for downloaded models
        return false
    }
}

private class JvmWhisperLocalEngine(
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
            message = "Local Whisper inference is not yet implemented on JVM. " +
                     "Please use OpenAIWhisperEngine for cloud-based Whisper transcription.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // No resources to release yet
    }
}

