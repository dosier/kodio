package space.kodio.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * High-level wrapper around [AudioPlaybackSession] providing a simplified API.
 * 
 * ## Example Usage
 * ```kotlin
 * val player = Kodio.player()
 * 
 * // Load and play a recording
 * player.load(recording)
 * player.start()
 * 
 * // Control playback
 * player.pause()
 * player.resume()
 * player.stop()
 * 
 * // Clean up
 * player.release()
 * 
 * // Or use with structured concurrency
 * Kodio.play(recording) { player ->
 *     player.start()
 *     player.awaitComplete()
 * } // auto-released
 * ```
 */
class Player internal constructor(
    private val session: AudioPlaybackSession
) {
    /**
     * The current unified state of the player.
     */
    val sessionState: AudioSessionState
        get() = session.state.value.toSessionState()

    /**
     * Flow of state changes for observing the player.
     */
    val stateFlow: StateFlow<AudioPlaybackSession.State>
        get() = session.state

    /**
     * Whether the player is currently playing audio.
     */
    val isPlaying: Boolean
        get() = session.state.value is AudioPlaybackSession.State.Playing

    /**
     * Whether the player is paused.
     */
    val isPaused: Boolean
        get() = session.state.value is AudioPlaybackSession.State.Paused

    /**
     * Whether the player is ready to play (has audio loaded).
     * This includes Ready, Paused, and Finished states where audio is available.
     */
    val isReady: Boolean
        get() = session.audioFlow.value != null && 
                session.state.value.let { state ->
                    state is AudioPlaybackSession.State.Ready ||
                    state is AudioPlaybackSession.State.Paused ||
                    state is AudioPlaybackSession.State.Finished
                }

    /**
     * Whether playback has finished.
     */
    val isFinished: Boolean
        get() = session.state.value is AudioPlaybackSession.State.Finished

    /**
     * Loads an [AudioRecording] for playback.
     */
    suspend fun load(recording: AudioRecording) {
        session.load(recording.asAudioFlow())
    }

    /**
     * Loads an [AudioFlow] for playback (legacy compatibility).
     */
    suspend fun loadAudioFlow(audioFlow: AudioFlow) {
        session.load(audioFlow)
    }

    /**
     * Starts playback.
     * 
     * @throws AudioError if no audio is loaded or playback fails
     */
    suspend fun start() {
        session.play()
    }

    /**
     * Pauses playback. Can be resumed with [resume].
     */
    fun pause() {
        session.pause()
    }

    /**
     * Resumes paused playback.
     */
    fun resume() {
        session.resume()
    }

    /**
     * Stops playback and resets to the beginning.
     */
    fun stop() {
        session.stop()
    }

    /**
     * Toggles between playing and paused states.
     * 
     * @return true if now playing, false if paused/stopped, null if no action taken
     */
    suspend fun toggle(): Boolean? {
        return when (session.state.value) {
            is AudioPlaybackSession.State.Playing -> {
                pause()
                false
            }
            is AudioPlaybackSession.State.Paused -> {
                resume()
                true
            }
            is AudioPlaybackSession.State.Ready,
            is AudioPlaybackSession.State.Finished -> {
                start()
                true
            }
            else -> null // No audio loaded or in error state
        }
    }

    /**
     * Waits for playback to complete (finish or error).
     */
    suspend fun awaitComplete() {
        session.state.first {
            it is AudioPlaybackSession.State.Finished ||
            it is AudioPlaybackSession.State.Error
        }
    }

    /**
     * Releases resources associated with this player.
     * The player should not be used after calling this method.
     */
    fun release() {
        session.stop()
    }

    /**
     * Access the underlying session for advanced use cases.
     */
    @Deprecated(
        message = "Use the Player API directly. This is for migration/compatibility only.",
        level = DeprecationLevel.WARNING
    )
    fun underlyingSession(): AudioPlaybackSession = session
}

/**
 * Extension for using Player with Kotlin's use pattern.
 * Ensures proper cleanup even if an exception occurs.
 */
inline fun <T> Player.use(block: (Player) -> T): T {
    try {
        return block(this)
    } finally {
        release()
    }
}
