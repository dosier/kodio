package space.kodio.core

/**
 * Unified state machine for both recording and playback sessions.
 * 
 * This provides a consistent mental model for users working with audio sessions,
 * regardless of whether they're recording or playing back audio.
 */
sealed interface AudioSessionState {

    /**
     * Session is idle and ready to start.
     */
    data object Idle : AudioSessionState

    /**
     * Session is actively recording or playing.
     */
    data object Active : AudioSessionState

    /**
     * Playback is paused (only applicable to playback sessions).
     */
    data object Paused : AudioSessionState

    /**
     * Session completed naturally (recording stopped or playback finished).
     */
    data object Complete : AudioSessionState

    /**
     * Session failed with an error.
     */
    data class Failed(val error: AudioError) : AudioSessionState
}

/**
 * Check if a state represents an active session.
 */
val AudioSessionState.isActive: Boolean
    get() = this == AudioSessionState.Active

/**
 * Check if a state represents a terminal state (Complete or Failed).
 */
val AudioSessionState.isTerminal: Boolean
    get() = this == AudioSessionState.Complete || this is AudioSessionState.Failed

/**
 * Check if a state allows starting a new operation.
 */
val AudioSessionState.canStart: Boolean
    get() = this == AudioSessionState.Idle || this == AudioSessionState.Complete

/**
 * Extension to convert legacy recording session states to the unified state.
 */
fun AudioRecordingSession.State.toSessionState(): AudioSessionState = when (this) {
    is AudioRecordingSession.State.Idle -> AudioSessionState.Idle
    is AudioRecordingSession.State.Recording -> AudioSessionState.Active
    is AudioRecordingSession.State.Stopped -> AudioSessionState.Complete
    is AudioRecordingSession.State.Error -> AudioSessionState.Failed(AudioError.from(error))
}

/**
 * Extension to convert legacy playback session states to the unified state.
 */
fun AudioPlaybackSession.State.toSessionState(): AudioSessionState = when (this) {
    is AudioPlaybackSession.State.Idle -> AudioSessionState.Idle
    is AudioPlaybackSession.State.Ready -> AudioSessionState.Idle
    is AudioPlaybackSession.State.Playing -> AudioSessionState.Active
    is AudioPlaybackSession.State.Paused -> AudioSessionState.Paused
    is AudioPlaybackSession.State.Finished -> AudioSessionState.Complete
    is AudioPlaybackSession.State.Error -> AudioSessionState.Failed(AudioError.from(error))
}
