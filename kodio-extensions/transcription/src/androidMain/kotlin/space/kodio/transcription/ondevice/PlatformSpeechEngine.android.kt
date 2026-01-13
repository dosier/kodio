package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * Android implementation of platform speech engine.
 * 
 * TODO: Implement using Android's SpeechRecognizer API.
 * This will require an Activity or Application context.
 */
actual object PlatformSpeechEngine {
    
    actual fun create(): TranscriptionEngine = AndroidPlatformSpeechEngine
    
    // Android has SpeechRecognizer, but we need context to use it
    actual val isSupported: Boolean = true
}

private object AndroidPlatformSpeechEngine : TranscriptionEngine {
    override val provider = TranscriptionProvider.PLATFORM_NATIVE
    
    // TODO: Set to true once implemented with proper context
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "Android SpeechRecognizer integration is not yet implemented. " +
                     "Please use a cloud-based engine like DeepgramEngine.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // TODO: Release SpeechRecognizer resources
    }
}

