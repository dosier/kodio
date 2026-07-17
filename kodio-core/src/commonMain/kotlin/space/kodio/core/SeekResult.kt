package space.kodio.core

/**
 * Outcome of a transport-level [AudioPlaybackSession.seekTo] attempt.
 *
 * Platforms that can reposition playback without tearing down the session
 * return [Applied]. Platforms that cannot return [Unsupported]; higher layers
 * (e.g. Compose [space.kodio.compose.PlayerState]) may then apply a portable
 * PCM-window fallback.
 */
sealed class SeekResult {
    /** Native transport applied the seek. */
    data object Applied : SeekResult()

    /** This session cannot seek in-place; callers may use a portable fallback. */
    data object Unsupported : SeekResult()

    /** `true` when the seek was applied natively. */
    val isApplied: Boolean
        get() = this is Applied
}
