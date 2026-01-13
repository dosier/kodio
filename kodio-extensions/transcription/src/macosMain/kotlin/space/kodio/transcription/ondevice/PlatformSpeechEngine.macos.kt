package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * macOS implementation of platform speech engine.
 * 
 * TODO: Implement using macOS Speech Framework (SFSpeechRecognizer).
 */
actual object PlatformSpeechEngine {
    
    actual fun create(): TranscriptionEngine = MacosPlatformSpeechEngine
    
    // macOS has Speech Framework available (10.15+)
    actual val isSupported: Boolean = true
}

private object MacosPlatformSpeechEngine : TranscriptionEngine {
    override val provider = TranscriptionProvider.PLATFORM_NATIVE
    
    // TODO: Set to true once implemented
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "macOS Speech Framework integration is not yet implemented. " +
                     "Please use a cloud-based engine like DeepgramEngine.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // TODO: Release Speech Framework resources
    }
}

