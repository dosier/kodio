package space.kodio.compose

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kodio.core.AudioFlow
import space.kodio.core.AudioPlaybackSession
import space.kodio.core.AudioQuality
import space.kodio.core.AudioRecording
import space.kodio.core.Player
import space.kodio.core.SeekResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Logic tests for [PlayerState] seek / scrub transport UI stability.
 *
 * Uses [UnconfinedTestDispatcher] so [Player] stateFlow updates are applied
 * eagerly (same as production Main.immediate-style collection) without needing
 * to drain the progress ticker's delayed loop via advanceUntilIdle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStateSeekTest {

    @Test
    fun seekWhilePlayingKeepsIsPlayingTrueThroughReload() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession()
        val state = playerState(session)

        state.loadAsync(testRecording())
        assertTrue(state.isReady)

        state.playAsync()
        assertTrue(state.isPlaying)

        // Hold the seek at the post-stop / pre-load gap so we can assert UI state.
        val loadGate = CompletableDeferred<Unit>()
        session.loadGate = loadGate

        state.seekTo(0.25f)
        runCurrent()

        assertTrue(
            state.isPlaying,
            "isPlaying should stay true while session reloads during play-through seek",
        )
        assertTrue(session.stopCalled)

        loadGate.complete(Unit)
        runCurrent()

        assertTrue(state.isPlaying)
        assertTrue(session.playCalled, "playback should resume after seek")
    }

    @Test
    fun seekWhilePausedKeepsIsPlayingFalse() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession()
        val state = playerState(session)

        state.loadAsync(testRecording())
        state.playAsync()
        state.pause()
        assertFalse(state.isPlaying)
        assertTrue(state.isPaused)

        session.resetPlayFlags()
        state.seekToAsync(0.4f)

        assertFalse(state.isPlaying, "paused scrub must not look like playing")
        assertFalse(session.playCalled, "paused seek must not auto-resume")
    }

    @Test
    fun rapidSeekPreservesPlayThroughIntent() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession()
        val state = playerState(session)

        state.loadAsync(testRecording())
        state.playAsync()
        assertTrue(state.isPlaying)

        state.seekTo(0.1f)
        state.seekTo(0.2f)
        state.seekTo(0.3f)
        // Between cancelled seeks the session is not Playing, but UI must stay "playing".
        assertTrue(state.isPlaying)
        runCurrent()
        assertTrue(state.isPlaying)
    }

    @Test
    fun pauseDuringSeekClearsPlayingUi() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession()
        val state = playerState(session)

        state.loadAsync(testRecording())
        state.playAsync()

        val loadGate = CompletableDeferred<Unit>()
        session.loadGate = loadGate

        state.seekTo(0.5f)
        runCurrent()
        assertTrue(state.isPlaying)

        state.pause()
        assertFalse(state.isPlaying)
        assertTrue(state.isPaused)

        loadGate.complete(Unit)
        runCurrent()
        assertFalse(state.isPlaying)
    }

    @Test
    fun rapidPlayThenPauseDoesNotResurrectPlaying() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession()
        val state = playerState(session)

        state.loadAsync(testRecording())

        val playGate = CompletableDeferred<Unit>()
        session.playGate = playGate

        state.play()
        runCurrent()
        state.pause()
        assertFalse(state.isPlaying)
        assertTrue(state.isPaused)

        playGate.complete(Unit)
        runCurrent()

        assertFalse(
            state.isPlaying,
            "late session Playing from in-flight play must not overwrite pause",
        )
        assertTrue(state.isPaused)
        assertTrue(session.pauseCalled)
    }

    @Test
    fun nativeSeekIsPreferredOverWindowReload() = runTest(UnconfinedTestDispatcher()) {
        val session = ControllablePlaybackSession(nativeSeek = true)
        val state = playerState(session)

        state.loadAsync(testRecording())
        state.playAsync()
        session.resetPlayFlags()

        state.seekToAsync(0.3f)

        assertTrue(session.seekCalled, "native seek should be attempted")
        assertFalse(session.stopCalled, "native seek must not fall back to stop/reload")
        assertEquals(0.3f, state.progress, absoluteTolerance = 0.001f)
        assertTrue(state.isPlaying)
    }

    private fun TestScope.playerState(session: ControllablePlaybackSession): PlayerState {
        return PlayerState(
            scope = backgroundScope,
            device = null,
            getOnPlaybackComplete = { null },
            playerFactory = { Player(session) },
        )
    }

    private fun testRecording(): AudioRecording {
        // Enough PCM for non-trivial seek windows (16-bit mono Standard).
        val format = AudioQuality.Standard.format
        val bytes = ByteArray(format.bytesPerFrame * 200) { (it % 17).toByte() }
        return AudioRecording.fromPcmBytes(format, bytes)
    }
}

/**
 * Minimal session that mirrors the Idle→Ready→Playing transitions of a real seek reload.
 */
private class ControllablePlaybackSession(
    private val nativeSeek: Boolean = false,
) : AudioPlaybackSession {
    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow

    var playCalled = false
        private set
    var stopCalled = false
        private set
    var pauseCalled = false
        private set
    var seekCalled = false
        private set

    /** When set, [load] suspends until the deferred completes (seek mid-gap). */
    var loadGate: CompletableDeferred<Unit>? = null

    /** When set, [play] suspends until the deferred completes (play→pause race). */
    var playGate: CompletableDeferred<Unit>? = null

    fun resetPlayFlags() {
        playCalled = false
        stopCalled = false
        pauseCalled = false
        seekCalled = false
    }

    override suspend fun load(audioFlow: AudioFlow) {
        loadGate?.await()
        _audioFlow.value = audioFlow
        _state.value = AudioPlaybackSession.State.Ready
    }

    override suspend fun play() {
        playGate?.await()
        playCalled = true
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun pause() {
        pauseCalled = true
        _state.value = AudioPlaybackSession.State.Paused
    }

    override fun resume() {
        playCalled = true
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun stop() {
        stopCalled = true
        _state.value = AudioPlaybackSession.State.Idle
    }

    override fun seekTo(position: Duration): SeekResult {
        seekCalled = true
        if (!nativeSeek) return SeekResult.Unsupported
        // Native seek keeps transport state; only updates position at the PlayerState layer.
        return SeekResult.Applied
    }
}
