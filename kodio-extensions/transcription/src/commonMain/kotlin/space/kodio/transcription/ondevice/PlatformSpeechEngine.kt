package space.kodio.transcription.ondevice

import space.kodio.transcription.TranscriptionEngine

/**
 * Factory for creating platform-native speech recognition engines.
 * 
 * This provides access to built-in speech recognition APIs on each platform:
 * - Android: SpeechRecognizer API
 * - iOS/macOS: Speech Framework
 * - Web: Web Speech API
 * - JVM: Falls back to cloud-based engines
 * 
 * ## Example
 * ```kotlin
 * val engine = PlatformSpeechEngine.create()
 * if (engine.isAvailable) {
 *     audioFlow.transcribe(engine).collect { result ->
 *         println(result)
 *     }
 * }
 * ```
 */
expect object PlatformSpeechEngine {
    /**
     * Creates a platform-native speech recognition engine.
     * 
     * @return A [TranscriptionEngine] using the platform's built-in speech recognition,
     *         or a stub engine if not available on this platform.
     */
    fun create(): TranscriptionEngine
    
    /**
     * Whether platform-native speech recognition is available.
     */
    val isSupported: Boolean
}

