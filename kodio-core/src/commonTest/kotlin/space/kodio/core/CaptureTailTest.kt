package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression: audio a platform hands over just after stop() (the Web Audio
 * worklet's final partial batch) must still end up in the recording.
 */
class CaptureTailTest {

    @Test
    fun getRecording_includesTailDeliveredAfterStop() = runRealTimeTest {
        val session = TailRecordingSession()
        val recorder = Recorder(session, AudioQuality.Standard)

        recorder.start()
        withTimeout(2_000) { session.firstChunkSent.await() }
        recorder.stop()

        // The platform delivers its last buffer a moment after stop() returned.
        session.tail.complete(listOf(ByteArray(TAIL_BYTES) { 7 }))

        val recording = assertNotNull(recorder.getRecording())
        assertEquals((CHUNK_BYTES + TAIL_BYTES).toLong(), recording.sizeInBytes)
        assertEquals(7, recording.toByteArray().last().toInt())
    }

    @Test
    fun sessionAudioFlow_updatesWithTailAndExactByteCount() = runRealTimeTest {
        val session = TailRecordingSession()
        session.start()
        withTimeout(2_000) { session.firstChunkSent.await() }
        session.stop()
        session.tail.complete(listOf(ByteArray(TAIL_BYTES)))

        session.awaitCaptureComplete()
        val flow = assertNotNull(session.audioFlow.value)
        assertEquals((CHUNK_BYTES + TAIL_BYTES).toLong(), flow.knownByteCount)
    }

    private class TailRecordingSession : BaseAudioRecordingSession() {
        val tail = CompletableDeferred<List<ByteArray>>()
        val firstChunkSent = CompletableDeferred<Unit>()

        override suspend fun prepareRecording(): AudioFormat = AudioQuality.Standard.format

        override suspend fun startRecording(channel: SendChannel<ByteArray>) {
            channel.send(ByteArray(CHUNK_BYTES) { 1 })
            // send() only queues the chunk; give the base session's collector time
            // to record it before the test calls stop().
            delay(100)
            firstChunkSent.complete(Unit)
            awaitCancellation()
        }

        override fun cleanup() = Unit

        override fun captureTail(): Deferred<List<ByteArray>> = tail
    }

    private companion object {
        const val CHUNK_BYTES = 400
        const val TAIL_BYTES = 120
    }
}
