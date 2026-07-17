package space.kodio.core

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Represents an active playback session.
 *
 * ## Seek capability
 *
 * [seekTo] is an optional native transport capability. The default implementation
 * returns [SeekResult.Unsupported]. Platforms that can reposition playback without
 * reloading audio override it and return [SeekResult.Applied], but only for
 * known-finite content ([AudioFlow.isKnownFinite]). Live/unbounded flows must
 * never be fully collected for seek.
 *
 * When [seekTo] returns [SeekResult.Unsupported], higher layers should fall back
 * to a portable approach (Compose [space.kodio.compose.PlayerState] reloads a
 * clipped PCM window via [AudioRecording.fromPcmBytes]). Keep that fallback out
 * of individual platform sessions so core stays capability-oriented rather than
 * branching on every target.
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
     * Attempts a platform-native seek to [position] within the currently loaded audio.
     *
     * @return [SeekResult.Applied] if the native transport repositioned playback;
     * [SeekResult.Unsupported] if this session cannot seek in-place. When unsupported,
     * callers should use a fallback (e.g. PCM window reload in Compose).
     */
    fun seekTo(position: Duration): SeekResult = SeekResult.Unsupported

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
