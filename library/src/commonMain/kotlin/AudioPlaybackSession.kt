import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active playback session.
 */
interface AudioPlaybackSession {

    /** A flow that emits the current state of the playback. */
    val state: StateFlow<AudioPlaybackState>
    
    /** Starts playback of the given audio data. */
    suspend fun play(audioFlow: AudioFlow)
    
    /** Pauses the playback. */
    fun pause()
    
    /** Resumes paused playback. */
    fun resume()

    /** Stops the playback entirely. */
    fun stop()
}