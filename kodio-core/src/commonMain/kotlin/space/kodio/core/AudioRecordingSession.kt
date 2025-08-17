package space.kodio.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active recording session.
 */
interface AudioRecordingSession {

    /** A flow that emits the current state of the recording. */
    val state: StateFlow<State>
    
    /** A flow that emits audio data chunks as ByteArrays while recording. */
    val audioFlow: StateFlow<AudioFlow?>

    /** Starts the recording. Sets the state to RECORDING. */
    suspend fun start()

    /** Stops the current recording. Sets the state to STOPPED. */
    fun stop()

    fun reset()

    /**
     * Represents the state of a recording session.
     */
    sealed class State {
        data object Idle : State()
        class Recording(val flow: AudioFlow) : State()
        data object Stopped : State()
        data class Error(val error: Throwable) : State()
    }
}