package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * Frozen defaults for unmigrated sessions / external implementors.
 * One shared instance each so collectors observe a stable reference.
 */
private val DefaultPositionFlow: StateFlow<Duration> =
    MutableStateFlow(Duration.ZERO).asStateFlow()

private val DefaultBufferedRangeFlow: StateFlow<BufferedRange> =
    MutableStateFlow(BufferedRange.Empty).asStateFlow()

/**
 * Represents an active playback session.
 *
 * ## Seek capability
 *
 * [seekTo] is an optional native transport capability. The default implementation
 * returns [SeekResult.Unsupported]. Platforms that wire [PlaybackBuffer]-backed
 * transport override it and return [SeekResult.Applied] when the target is inside
 * [bufferedRange], or ahead of ingest on known-finite content (pending cursor).
 * Live/unbounded flows must never be fully collected for seek; seek outside the
 * retained window returns [SeekResult.Unsupported].
 *
 * When [seekTo] returns [SeekResult.Unsupported], higher layers should fall back
 * to a portable approach (Compose [space.kodio.compose.PlayerState] reloads a
 * clipped PCM window via [AudioRecording.fromPcmBytes]). Keep that fallback out
 * of individual platform sessions so core stays capability-oriented rather than
 * branching on every target.
 *
 * [knownByteCount] / [AudioFlow.isKnownFinite] are length metadata and growable
 * eligibility — not the sole seek gate.
 */
interface AudioPlaybackSession {

    /** A flow that emits the current state of the playback. */
    val state: StateFlow<State>

    /** A flow that emits the current loaded audio data. */
    val audioFlow: StateFlow<AudioFlow?>

    /**
     * Transport media time, best-effort while playing.
     * Defaults to a frozen [Duration.ZERO] for unmigrated sessions.
     */
    val position: StateFlow<Duration>
        get() = DefaultPositionFlow

    /**
     * Currently retained seekable window. Empty if nothing buffered.
     * Defaults to a frozen empty range for unmigrated sessions.
     */
    val bufferedRange: StateFlow<BufferedRange>
        get() = DefaultBufferedRangeFlow

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
     * Move the transport read cursor.
     *
     * Does not stop ingest. Flushes/restarts device feed as needed.
     * [SeekResult.Applied]: target inside [bufferedRange], or ahead of ingest on
     * known-finite content (plays once data arrives). [SeekResult.Unsupported]
     * otherwise.
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
