package space.kodio.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * High-level wrapper around [AudioRecordingSession] providing a simplified API.
 * 
 * ## Example Usage
 * ```kotlin
 * val recorder = Kodio.recorder()
 * 
 * recorder.start()
 * // Recording in progress...
 * 
 * // Stop and get result
 * recorder.stop()
 * val recording = recorder.getRecording()
 * 
 * // Clean up
 * recorder.release()
 * 
 * // Or use with structured concurrency
 * Kodio.record { recorder ->
 *     recorder.start()
 *     delay(5.seconds)
 *     recorder.stop()
 *     recorder.getRecording()
 * } // auto-released
 * ```
 * 
 * @property quality The requested audio quality
 */
class Recorder internal constructor(
    private val session: AudioRecordingSession,
    val quality: AudioQuality
) {
    
    // Cached recording to avoid re-collecting on every access
    private var cachedRecording: AudioRecording? = null
    private var cachedForState: AudioRecordingSession.State? = null

    /**
     * The current unified state of the recorder.
     */
    val sessionState: AudioSessionState
        get() = session.state.value.toSessionState()

    /**
     * Flow of state changes for observing the recorder.
     */
    val stateFlow: StateFlow<AudioRecordingSession.State>
        get() = session.state

    /**
     * Whether the recorder is currently recording.
     */
    val isRecording: Boolean
        get() = session.state.value is AudioRecordingSession.State.Recording

    /**
     * Whether the recorder is paused.
     * Note: The underlying session may not support pausing.
     */
    val isPaused: Boolean
        get() = false // Recording sessions don't have a paused state currently

    /**
     * The audio format of this recorder (derived from quality).
     */
    val format: AudioFormat
        get() = quality.format

    /**
     * Whether the recorder is in stopped state with a recording available.
     */
    val hasRecording: Boolean
        get() = session.state.value is AudioRecordingSession.State.Stopped && 
                session.audioFlow.value != null

    /**
     * The live audio data as a Flow of byte arrays.
     * This emits chunks in real-time while recording.
     */
    val liveAudioFlow: Flow<ByteArray>?
        get() = session.audioFlow.value

    /**
     * The raw AudioFlow for advanced use cases.
     */
    val audioFlow: AudioFlow?
        get() = session.audioFlow.value

    /**
     * Gets the completed recording, if available.
     * 
     * This returns null while recording is in progress and becomes available
     * after [stop] is called. The result is cached for efficiency.
     * 
     * This is a suspend function to safely collect the audio data.
     */
    suspend fun getRecording(): AudioRecording? {
        val currentState = session.state.value
        val audioFlow = session.audioFlow.value ?: return null
        
        // Return cached if state hasn't changed
        if (currentState == cachedForState && cachedRecording != null) {
            return cachedRecording
        }
        
        // Only available in stopped state
        if (currentState !is AudioRecordingSession.State.Stopped) {
            return null
        }
        
        // Collect and cache
        val recording = AudioRecording.fromAudioFlow(audioFlow)
        cachedRecording = recording
        cachedForState = currentState
        return recording
    }

    /**
     * Starts recording audio.
     * 
     * @throws AudioError.PermissionDenied if microphone access is denied
     */
    suspend fun start() {
        if (isRecording) return
        // Clear cache when starting new recording
        cachedRecording = null
        cachedForState = null
        session.start()
    }

    /**
     * Stops the current recording.
     * After calling this, [getRecording] will return the recorded audio.
     */
    fun stop() {
        session.stop()
    }

    /**
     * Toggles recording on/off.
     * 
     * @return true if now recording, false if stopped
     */
    suspend fun toggle(): Boolean {
        return if (isRecording) {
            stop()
            false
        } else {
            start()
            true
        }
    }

    /**
     * Resets the recorder to idle state, discarding any recorded audio.
     */
    fun reset() {
        cachedRecording = null
        cachedForState = null
        session.reset()
    }

    /**
     * Releases resources associated with this recorder.
     * The recorder should not be used after calling this method.
     */
    fun release() {
        if (isRecording) {
            session.stop()
        }
        cachedRecording = null
        cachedForState = null
        session.reset()
    }

    /**
     * Waits for the recording to complete (stop being called externally or error).
     */
    suspend fun awaitComplete() {
        session.state.first { 
            it is AudioRecordingSession.State.Stopped || 
            it is AudioRecordingSession.State.Error 
        }
    }

    /**
     * Access the underlying session for advanced use cases.
     */
    @Deprecated(
        message = "Use the Recorder API directly. This is for migration/compatibility only.",
        level = DeprecationLevel.WARNING
    )
    fun underlyingSession(): AudioRecordingSession = session
}

/**
 * Extension for using Recorder with Kotlin's use pattern.
 * Ensures proper cleanup even if an exception occurs.
 */
inline fun <T> Recorder.use(block: (Recorder) -> T): T {
    try {
        return block(this)
    } finally {
        release()
    }
}
