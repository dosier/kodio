import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents an active playback session.
 */
interface PlaybackSession {

    /** A flow that emits the current state of the playback. */
    val state: StateFlow<PlaybackState>
    
    /** Starts playback of the given audio data. */
    suspend fun play(dataFlow: Flow<ByteArray>, format: AudioFormat)
    
    /** Pauses the playback. */
    fun pause()
    
    /** Resumes paused playback. */
    fun resume()

    /** Stops the playback entirely. */
    fun stop()
}