package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import space.kodio.core.transport.AudioSink
import space.kodio.core.transport.FakeAudioSink
import space.kodio.core.transport.VirtualClock
import space.kodio.core.transport.bytesForDuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Base-session transport wiring: ingest ⊥ feed, seek semantics, hot-flow guard.
 *
 * Uses [runBlocking] (not runTest virtual time) because [BaseAudioPlaybackSession]
 * schedules ingest/feed on [Dispatchers.Default].
 */
class TransportPlaybackSessionTest {

    private val format = AudioFormat(
        sampleRate = 16_000,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            signed = true,
            layout = SampleLayout.Interleaved,
        ),
    )

    @Test
    fun finiteSeek_zeroMidEnd_applied() = runBlocking {
        val frames = 1600
        val pcm = ByteArray(frames * format.bytesPerFrame) { 1 }
        val session = FakeTransportSession(bufferConfig = PlaybackBufferConfig(maxBytes = 1L shl 20))
        session.load(AudioFlow(format, flowOf(pcm), knownByteCount = pcm.size.toLong()))

        session.play()
        withTimeout(2_000) { session.firstWrite.await() }

        assertEquals(SeekResult.Applied, session.seekTo(0.milliseconds))
        assertEquals(SeekResult.Applied, session.seekTo(50.milliseconds))
        val endMinusOneFrame = format.durationOfByteOffset((frames - 1L) * format.bytesPerFrame)
        assertEquals(SeekResult.Applied, session.seekTo(endMinusOneFrame))

        session.stop()
    }

    @Test
    fun liveSeek_insideWindow_applied_pastEdge_unsupported() = runBlocking {
        val config = PlaybackBufferConfig(
            maxBytes = format.bytesForDuration(10.seconds),
            maxDuration = 10.seconds,
        )
        val session = FakeTransportSession(bufferConfig = config)
        val hot = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        session.load(AudioFlow(format, hot.asSharedFlow()))

        session.play()
        withTimeout(2_000) { session.ingestStarted.await() }

        val chunkFrames = 160 // 10ms
        val chunk = ByteArray(chunkFrames * format.bytesPerFrame) { 2 }
        repeat(400) { hot.emit(chunk) } // 4s
        withTimeout(2_000) { session.firstWrite.await() }

        assertEquals(SeekResult.Applied, session.seekTo(1.seconds))
        assertEquals(SeekResult.Unsupported, session.seekTo(9.seconds)) // past write edge

        session.stop()
    }

    @Test
    fun hotFlow_neverDrainedBeforePlaybackStarts() = runBlocking {
        val session = FakeTransportSession()
        val started = CompletableDeferred<Unit>()
        val neverCompletes = AudioFlow(
            format,
            flow {
                started.complete(Unit)
                emit(ByteArray(format.bytesPerFrame * 16) { 3 })
                kotlinx.coroutines.awaitCancellation()
            },
        )
        session.load(neverCompletes)
        session.play()
        withTimeout(2_000) { started.await() }
        withTimeout(2_000) { session.firstWrite.await() }
        assertFalse(session.sink.writes.isEmpty())
        session.stop()
    }

    @Test
    fun seekAfterFinish_retainsBuffer_applied() = runBlocking {
        val pcm = ByteArray(format.bytesPerFrame * 80) { 4 }
        val session = FakeTransportSession()
        session.load(AudioFlow(format, flowOf(pcm), knownByteCount = pcm.size.toLong()))
        session.play()
        withTimeout(5_000) {
            while (session.state.value !is AudioPlaybackSession.State.Finished) {
                delay(5)
            }
        }
        assertIs<AudioPlaybackSession.State.Finished>(session.state.value)
        assertEquals(SeekResult.Applied, session.seekTo(1.milliseconds))
        assertIs<AudioPlaybackSession.State.Paused>(session.state.value)
        session.stop()
    }

    @Test
    fun pendingCursor_finiteAheadOfIngest_applied() = runBlocking {
        val session = FakeTransportSession()
        val gate = CompletableDeferred<Unit>()
        val pcm = ByteArray(format.bytesPerFrame * 160) { 5 } // 10ms
        val flow = AudioFlow(
            format,
            flow {
                gate.await()
                emit(pcm)
            },
            knownByteCount = pcm.size.toLong(),
        )
        session.load(flow)
        session.play()
        withTimeout(2_000) { session.ingestStarted.await() }

        assertEquals(SeekResult.Applied, session.seekTo(5.milliseconds))

        gate.complete(Unit)
        withTimeout(2_000) { session.firstWrite.await() }
        val first = session.sink.writes.first()
        // 5ms at 16 kHz mono s16 ≈ 160 bytes
        assertTrue(
            first.mediaByteOffset >= 160L - format.bytesPerFrame,
            "expected feed from pending cursor, got offset=${first.mediaByteOffset}",
        )
        session.stop()
    }
}

private class FakeTransportSession(
    override val bufferConfig: PlaybackBufferConfig = PlaybackBufferConfig(),
) : BaseAudioPlaybackSession() {

    val clock = VirtualClock()
    val sink = FakeAudioSink(clock)
    val firstWrite = CompletableDeferred<Unit>()
    val ingestStarted = CompletableDeferred<Unit>()

    override fun useTransportPlayback(): Boolean = true

    override fun createAudioSink(format: AudioFormat): AudioSink = object : AudioSink {
        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
            seekEpoch: Long,
            mediaByteOffset: Long,
        ) {
            sink.write(data, offset, length, seekEpoch, mediaByteOffset)
            firstWrite.complete(Unit)
        }

        override fun flush(seekEpoch: Long) = sink.flush(seekEpoch)
    }

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat {
        ingestStarted.complete(Unit)
        return format
    }

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        error("unused")
    }

    override fun onPause() = Unit
    override fun onResume() = Unit
    override fun onStop() = Unit
}
