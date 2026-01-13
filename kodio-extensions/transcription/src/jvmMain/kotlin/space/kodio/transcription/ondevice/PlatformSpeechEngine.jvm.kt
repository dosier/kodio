package space.kodio.transcription.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import space.kodio.core.AudioFlow
import space.kodio.transcription.*

/**
 * JVM implementation of platform speech engine.
 * 
 * JVM/Desktop doesn't have a native speech recognition API,
 * so this returns a stub that directs users to cloud engines.
 */
actual object PlatformSpeechEngine {
    
    actual fun create(): TranscriptionEngine = JvmPlatformSpeechEngine
    
    actual val isSupported: Boolean = false
}

private object JvmPlatformSpeechEngine : TranscriptionEngine {
    override val provider = TranscriptionProvider.PLATFORM_NATIVE
    override val isAvailable = false
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flowOf(
        TranscriptionResult.Error(
            message = "Platform speech recognition is not available on JVM/Desktop. " +
                     "Please use a cloud-based engine like DeepgramEngine or AssemblyAIEngine.",
            code = "PLATFORM_NOT_SUPPORTED",
            isRecoverable = false
        )
    )
    
    override fun release() {
        // No resources to release
    }
}

