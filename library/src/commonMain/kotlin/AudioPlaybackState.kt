
/**
 * Represents the state of a playback session.
 */
sealed class AudioPlaybackState {
    data object Idle : AudioPlaybackState()
    data object Playing : AudioPlaybackState()
    data object Paused : AudioPlaybackState()
    data object Finished : AudioPlaybackState()
    data class Error(val error: Throwable) : AudioPlaybackState()
}