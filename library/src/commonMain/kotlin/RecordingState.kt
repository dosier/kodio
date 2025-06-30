/**
 * Represents the state of a recording session.
 */
sealed class RecordingState {
    data object Idle : RecordingState()
    data object Recording : RecordingState()
    data object Stopped : RecordingState()
    data class Error(val error: Throwable) : RecordingState()
}
