package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * Web implementation of platform speech engine.
 * 
 * TODO: Implement using Web Speech API (SpeechRecognition).
 * Note: Web Speech API support varies by browser.
 */
actual object PlatformSpeechEngine {
    
    actual fun create(): TranscriptionEngine = WebPlatformSpeechEngine
    
    // Web Speech API is available in most modern browsers
    actual val isSupported: Boolean = true
}

private object WebPlatformSpeechEngine : TranscriptionEngine {
    override val provider = TranscriptionProvider.PLATFORM_NATIVE
    
    // TODO: Check for webkitSpeechRecognition or SpeechRecognition support
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "Web Speech API integration is not yet implemented. " +
                     "Please use a cloud-based engine like DeepgramEngine.",
            code = "NOT_IMPLEMENTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // TODO: Stop SpeechRecognition
    }
}

