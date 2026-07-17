package space.kodio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PlayerSeekTest {

    @Test
    fun seekTo_forwardsAppliedFromSession() = runTest {
        val session = FakeSeekSession(SeekResult.Applied)
        val player = Player(session)
        player.loadAudioFlow(
            AudioFlow(AudioQuality.Standard.format, flowOf(ByteArray(16))),
        )
        assertEquals(SeekResult.Applied, player.seekTo(250.milliseconds))
        assertEquals(250.milliseconds, session.lastSeekPosition)
    }

    @Test
    fun seekTo_forwardsUnsupportedFromSession() = runTest {
        val session = FakeSeekSession(SeekResult.Unsupported)
        val player = Player(session)
        player.loadAudioFlow(
            AudioFlow(AudioQuality.Standard.format, flowOf(ByteArray(16))),
        )
        assertIs<SeekResult.Unsupported>(player.seekTo(100.milliseconds))
    }
}

private class FakeSeekSession(
    private val result: SeekResult,
) : AudioPlaybackSession {
    private val _state = MutableStateFlow<AudioPlaybackSession.State>(AudioPlaybackSession.State.Idle)
    override val state: StateFlow<AudioPlaybackSession.State> = _state

    private val _audioFlow = MutableStateFlow<AudioFlow?>(null)
    override val audioFlow: StateFlow<AudioFlow?> = _audioFlow

    var lastSeekPosition: Duration? = null
        private set

    override suspend fun load(audioFlow: AudioFlow) {
        _audioFlow.value = audioFlow
        _state.value = AudioPlaybackSession.State.Ready
    }

    override suspend fun play() {
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun pause() {
        _state.value = AudioPlaybackSession.State.Paused
    }

    override fun resume() {
        _state.value = AudioPlaybackSession.State.Playing
    }

    override fun stop() {
        _state.value = AudioPlaybackSession.State.Idle
    }

    override fun seekTo(position: Duration): SeekResult {
        lastSeekPosition = position
        return result
    }
}
