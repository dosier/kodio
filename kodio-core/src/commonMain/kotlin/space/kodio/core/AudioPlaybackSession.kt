package space.kodio.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active playback session.
 */
interface AudioPlaybackSession {

    /** A flow that emits the current state of the playback. */
    val state: StateFlow<State>

    /** A flow that emits the current loaded audio data. */
    val audioFlow: StateFlow<AudioFlow?>

    /** Loads the given audio data. */
    suspend fun load(audioFlow: AudioFlow)

    /** Starts playback of the given audio data. */
    suspend fun play()

    /** Pauses the playback. */
    fun pause()
    
    /** Resumes paused playback. */
    fun resume()

    /** Stops the playback entirely. */
    fun stop()


    /**
     * Represents the state of a playback session.
     */
    sealed class State {
        data object Idle : State()
        data object Ready : State()
        data object Playing : State()
        data object Paused : State()
        data object Finished : State()
        data class Error(val error: Throwable) : State()
    }
}