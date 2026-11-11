package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Regression: rapid play → pause must not leave the session in Playing when
 * pause wins during prepare/convert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseAudioPlaybackSessionRaceTest {

    @Test
    fun pauseDuringPrepareWinsOverInFlightPlay() = runTest {
        val prepareGate = CompletableDeferred<Unit>()
        val session = GatedPlaybackSession(prepareGate)

        session.load(
            AudioFlow(
                format = AudioQuality.Standard.format,
                data = flowOf(ByteArray(32)),
            )
        )

        val play = async { session.play() }
        // Let play enter preparePlayback and block on the gate.
        yield()
        runCurrent()
        session.pause()
        assertIs<AudioPlaybackSession.State.Paused>(session.state.value)

        prepareGate.complete(Unit)
        play.await()

        assertIs<AudioPlaybackSession.State.Paused>(
            session.state.value,
            "in-flight play must not overwrite pause after prepare completes",
        )
        assertTrue(session.onPauseCalls >= 1)
        assertEquals(0, session.playBlockingCalls, "playBlocking must not start after pause won")
    }

    /**
     * Regression: a playback that is finishing must not clear the play intent of a
     * new play() that started in between (check-then-clear race). The new play then
     * aborted after prepare and never started. Stressed because the window is tiny.
     */
    @Test
    fun playRightAfterFinishAlwaysStarts() = runRealTimeTest(timeout = 60.seconds) {
        val session = ImmediateFinishSession()
        session.load(AudioFlow(AudioQuality.Standard.format, flowOf(ByteArray(32))))

        repeat(500) { i ->
            val started = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            session.started = started
            session.finished = finished
            session.play()
            withTimeout(5_000) { started.await() }
            // Replay as soon as playBlocking returns, while the old job is finishing.
            withTimeout(5_000) { finished.await() }
            assertTrue(
                session.state.value !is AudioPlaybackSession.State.Error,
                "iteration $i errored: ${session.state.value}",
            )
        }
        session.release()
    }

    @Test
    fun seekToDefaultsToUnsupported() = runTest {
        val session = GatedPlaybackSession(CompletableDeferred<Unit>().also { it.complete(Unit) })
        session.load(
            AudioFlow(
                format = AudioQuality.Standard.format,
                data = flowOf(ByteArray(8)),
            )
        )
        assertEquals(SeekResult.Unsupported, session.seekTo(100.milliseconds))
    }
}

/** Legacy-path session whose playBlocking returns at once. */
private class ImmediateFinishSession : BaseAudioPlaybackSession() {
    @kotlin.concurrent.Volatile
    var started = CompletableDeferred<Unit>()

    @kotlin.concurrent.Volatile
    var finished = CompletableDeferred<Unit>()

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat = format

    @Suppress("OVERRIDE_DEPRECATION") // exercises the legacy playBlocking path on purpose
    override suspend fun playBlocking(audioFlow: AudioFlow) {
        started.complete(Unit)
        finished.complete(Unit)
    }

    override fun onPause() = Unit
    override fun onResume() = Unit
    override fun onStop() = Unit
}

private class GatedPlaybackSession(
    private val prepareGate: CompletableDeferred<Unit>,
) : BaseAudioPlaybackSession() {

    var onPauseCalls = 0
        private set
    var playBlockingCalls = 0
        private set

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        prepareGate.await()
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        playBlockingCalls++
    }

    override fun onPause() {
        onPauseCalls++
    }

    override fun onResume() = Unit

    override fun onStop() = Unit
}
