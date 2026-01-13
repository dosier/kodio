package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * iOS implementation of platform speech engine.
 * 
 * TODO: Implement using iOS Speech Framework (SFSpeechRecognizer).
 */
actual object PlatformSpeechEngine {
    
    actual fun create(): TranscriptionEngine = IosPlatformSpeechEngine
    
    // iOS has Speech Framework available
    actual val isSupported: Boolean = true
}

private object IosPlatformSpeechEngine : TranscriptionEngine {
    override val provider = TranscriptionProvider.PLATFORM_NATIVE
    
    // TODO: Set to true once implemented
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "iOS Speech Framework integration is not yet implemented. " +
                     "Please use a cloud-based engine like DeepgramEngine.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // TODO: Release Speech Framework resources
    }
}

