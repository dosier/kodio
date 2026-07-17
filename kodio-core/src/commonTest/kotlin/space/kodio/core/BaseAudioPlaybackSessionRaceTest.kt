package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
