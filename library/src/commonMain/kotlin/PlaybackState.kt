
/**
 * Represents the state of a playback session.
 */
sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Playing : PlaybackState()
    data object Paused : PlaybackState()
    data object Finished : PlaybackState()
    data class Error(val error: Throwable) : PlaybackState()
}