package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import space.kodio.core.io.decodeAsAudioRecordingState
import space.kodio.core.io.encodeToByteArray

internal class AudioRecordingStateCodecTest {

    @Test
    fun `round-trip Idle`() {
        roundTripSingleton(AudioRecordingSession.State.Idle)
    }

    @Test
    fun `round-trip Recording`() {
        roundTripSingleton(AudioRecordingSession.State.Recording)
    }

    @Test
    fun `round-trip Paused`() {
        roundTripSingleton(AudioRecordingSession.State.Paused)
    }

    @Test
    fun `round-trip Stopped`() {
        roundTripSingleton(AudioRecordingSession.State.Stopped)
    }

    @Test
    fun `round-trip Error preserves message`() {
        val original = AudioRecordingSession.State.Error(RuntimeException("boom"))
        val decoded = original.encodeToByteArray().decodeAsAudioRecordingState()
        assertIs<AudioRecordingSession.State.Error>(decoded)
        assertEquals("boom", decoded.error.message)
    }

    @Test
    fun `Error with null message decodes to fallback`() {
        val original = AudioRecordingSession.State.Error(RuntimeException())
        val decoded = original.encodeToByteArray().decodeAsAudioRecordingState()
        assertIs<AudioRecordingSession.State.Error>(decoded)
        assertEquals("unknown error", decoded.error.message)
    }

    private fun roundTripSingleton(original: AudioRecordingSession.State) {
        val decoded = original.encodeToByteArray().decodeAsAudioRecordingState()
        assertEquals(original, decoded)
    }
}
