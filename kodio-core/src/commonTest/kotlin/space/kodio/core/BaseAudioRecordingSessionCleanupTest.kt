package space.kodio.core

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that stop/reset release the capture device via [BaseAudioRecordingSession]'s
 * platform [cleanup][BaseAudioRecordingSession.cleanup] hook, even when the producer is
 * blocked in a non-cancellable read loop.
 */
class BaseAudioRecordingSessionCleanupTest {

    @Test
    fun `stop calls cleanup even while producer is blocked`() = runTest {
        val session = BlockingRecordingSession()

        session.start()
        assertEquals(AudioRecordingSession.State.Recording, session.state.value)

        session.stop()

        assertEquals(1, session.cleanupCount)
        assertEquals(AudioRecordingSession.State.Stopped, session.state.value)
    }

    @Test
    fun `reset calls cleanup after stop is idempotent for device release`() = runTest {
        val session = BlockingRecordingSession()

        session.start()
        session.stop()
        assertEquals(1, session.cleanupCount)

        session.reset()
        // Once-flag already released the device; reset must not invoke cleanup again.
        assertEquals(1, session.cleanupCount)
        assertEquals(AudioRecordingSession.State.Idle, session.state.value)
    }

    @Test
    fun `stop while paused still releases the device`() = runTest {
        val session = BlockingRecordingSession(supportsPause = true)

        session.start()
        session.pause()
        assertEquals(AudioRecordingSession.State.Paused, session.state.value)

        session.stop()

        assertEquals(1, session.cleanupCount)
        assertEquals(AudioRecordingSession.State.Stopped, session.state.value)
    }

    @Test
    fun `Recorder release stops paused session and resets`() = runTest {
        val session = FakeRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        recorder.pause()
        assertTrue(recorder.isPaused)

        recorder.release()

        assertTrue(session.resetCalled)
        assertEquals(AudioRecordingSession.State.Idle, session.state.value)
    }

    /**
     * Simulates JVM/Android-style capture: [startRecording] parks until [cleanup] runs,
     * so job cancellation alone would never reach a late-registered awaitClose.
     */
    private class BlockingRecordingSession(
        private val supportsPause: Boolean = false,
    ) : BaseAudioRecordingSession() {
        var cleanupCount = 0
            private set

        @Volatile
        private var released = false

        override suspend fun prepareRecording(): AudioFormat = AudioQuality.Standard.format

        override suspend fun startRecording(channel: SendChannel<ByteArray>) {
            channel.send(byteArrayOf(1, 2, 3, 4))
            // Park until cleanup() closes the "device".
            while (!released) {
                delay(10)
            }
        }

        override fun cleanup() {
            cleanupCount++
            released = true
        }

        override suspend fun pauseRecording() {
            if (!supportsPause) {
                super.pauseRecording()
            }
        }

        override suspend fun resumeRecording() {
            if (!supportsPause) {
                super.resumeRecording()
            }
        }
    }
}
