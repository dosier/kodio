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

    /**
     * Pauses an active recording.
     *
     * The previously captured chunks remain in the recording buffer; calling
     * [resume] continues appending to the same audio stream (no data is lost,
     * unlike calling [stop] followed by another [start]).
     *
     * Throws `UnsupportedOperationException` for sessions that don't support
     * pausing. The default [BaseAudioRecordingSession] implementation supports
     * pause on every Kodio-shipped platform.
     *
     * No-op when the session is not currently in [State.Recording].
     */
    suspend fun pause(): Unit =
        throw UnsupportedOperationException("Pause is not supported by this AudioRecordingSession")

    /**
     * Resumes a [paused][State.Paused] recording.
     *
     * No-op when the session is not currently in [State.Paused].
     */
    suspend fun resume(): Unit =
        throw UnsupportedOperationException("Resume is not supported by this AudioRecordingSession")

    /** Stops the current recording. Sets the state to STOPPED. */
    fun stop()

    fun reset()

    /**
     * Represents the state of a recording session.
     */
    sealed class State {
        data object Idle : State()
        data object Recording : State()
        /** Capture has been paused via [pause]; data captured so far is preserved. */
        data object Paused : State()
        data object Stopped : State()
        data class Error(val error: Throwable) : State()
    }
}