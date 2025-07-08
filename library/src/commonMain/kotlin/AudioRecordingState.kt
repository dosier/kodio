/**
 * Represents the state of a recording session.
 */
sealed class AudioRecordingState {
    data object Idle : AudioRecordingState()
    data object Recording : AudioRecordingState()
    data object Stopped : AudioRecordingState()
    data class Error(val error: Throwable) : AudioRecordingState()
}
