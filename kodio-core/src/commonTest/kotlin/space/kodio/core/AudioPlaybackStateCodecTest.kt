package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import space.kodio.core.io.decodeAsAudioPlaybackState
import space.kodio.core.io.encodeToByteArray

internal class AudioPlaybackStateCodecTest {

    @Test
    fun `round-trip Idle`() {
        roundTripSingleton(AudioPlaybackSession.State.Idle)
    }

    @Test
    fun `round-trip Ready`() {
        roundTripSingleton(AudioPlaybackSession.State.Ready)
    }

    @Test
    fun `round-trip Playing`() {
        roundTripSingleton(AudioPlaybackSession.State.Playing)
    }

    @Test
    fun `round-trip Paused`() {
        roundTripSingleton(AudioPlaybackSession.State.Paused)
    }

    @Test
    fun `round-trip Finished`() {
        roundTripSingleton(AudioPlaybackSession.State.Finished)
    }

    @Test
    fun `round-trip Error preserves message`() {
        val original = AudioPlaybackSession.State.Error(RuntimeException("boom"))
        val decoded = original.encodeToByteArray().decodeAsAudioPlaybackState()
        assertIs<AudioPlaybackSession.State.Error>(decoded)
        assertEquals("boom", decoded.error.message)
    }

    @Test
    fun `Error with null message decodes to fallback`() {
        val original = AudioPlaybackSession.State.Error(RuntimeException())
        val decoded = original.encodeToByteArray().decodeAsAudioPlaybackState()
        assertIs<AudioPlaybackSession.State.Error>(decoded)
        assertEquals("unknown error", decoded.error.message)
    }

    private fun roundTripSingleton(original: AudioPlaybackSession.State) {
        val decoded = original.encodeToByteArray().decodeAsAudioPlaybackState()
        assertEquals(original, decoded)
    }
}
