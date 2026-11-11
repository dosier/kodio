package space.kodio.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kodio.core.io.audioFlowOf
import space.kodio.core.io.convertAudio
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AudioFlowKnownFiniteTest {

    private val format = AudioQuality.Standard.format

    @Test
    fun recordingAsAudioFlow_isKnownFinite() {
        val pcm = ByteArray(format.bytesPerFrame * 100)
        val recording = AudioRecording.fromPcmBytes(format, pcm)
        val flow = recording.asAudioFlow()
        assertTrue(flow.isKnownFinite)
        assertEquals(recording.sizeInBytes, flow.knownByteCount)
    }

    @Test
    fun audioFlowOf_isKnownFinite() {
        val flow = audioFlowOf(format, 1, 2, 3, 4)
        assertTrue(flow.isKnownFinite)
        assertEquals(4L, flow.knownByteCount)
    }

    @Test
    fun hotSharedFlow_isNotKnownFinite() {
        val hot = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        val flow = AudioFlow(format, hot.asSharedFlow())
        assertFalse(flow.isKnownFinite)
        assertNull(flow.knownByteCount)
    }

    @Test
    fun unboundedFlowBuilder_isNotKnownFinite() {
        val flow = AudioFlow(
            format,
            flow {
                emit(ByteArray(16))
                // never completes — typical of a live monitor
            },
        )
        assertFalse(flow.isKnownFinite)
    }

    @Test
    fun convertAudio_preservesKnownFiniteness() {
        val intFormat = AudioFormat(
            sampleRate = 16_000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.Sixteen,
                endianness = Endianness.Little,
                signed = true,
                layout = SampleLayout.Interleaved,
            ),
        )
        val floatFormat = AudioFormat(
            sampleRate = 16_000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmFloat(
                precision = FloatPrecision.F32,
                layout = SampleLayout.Interleaved,
            ),
        )
        val frames = 100
        val pcm = ByteArray(frames * intFormat.bytesPerFrame)
        val source = AudioFlow(intFormat, flowOf(pcm), knownByteCount = pcm.size.toLong())
        val converted = source.convertAudio(floatFormat)
        assertTrue(converted.isKnownFinite)
        assertNotNull(converted.knownByteCount)
        assertEquals(
            frames.toLong() * floatFormat.bytesPerFrame,
            converted.knownByteCount,
        )
    }

    @Test
    fun convertAudio_unboundedStaysUnbounded() {
        val target = AudioFormat(
            sampleRate = format.sampleRate,
            channels = format.channels,
            encoding = SampleEncoding.PcmFloat(
                precision = FloatPrecision.F32,
                layout = SampleLayout.Interleaved,
            ),
        )
        val source = AudioFlow(format, flowOf(ByteArray(32)))
        assertFalse(source.isKnownFinite)
        assertFalse(source.convertAudio(target).isKnownFinite)
    }
}

/**
 * Documents the session seek contract: buffer-backed seek only for known-finite
 * content; streaming/hot flows must get [SeekResult.Unsupported].
 */
class SeekGatingSessionTest {

    private val format = AudioQuality.Standard.format

    @Test
    fun seek_unsupportedForHotFlow() = runTest {
        val session = GatingSeekSession()
        val hot = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
        session.load(AudioFlow(format, hot.asSharedFlow()))
        assertEquals(SeekResult.Unsupported, session.seekTo(100.milliseconds))
        assertFalse(session.materialized)
    }

    @Test
    fun seek_appliedForKnownFinite_andPlayMaterializesOnce() = runTest {
        val session = GatingSeekSession()
        val pcm = ByteArray(format.bytesPerFrame * 50)
        val recording = AudioRecording.fromPcmBytes(format, pcm)
        session.load(recording.asAudioFlow())

        assertEquals(SeekResult.Applied, session.seekTo(50.milliseconds))

        session.play()
        session.playBlockingFinished.await()
        assertTrue(session.materialized)
        assertEquals(1, session.collectCount)

        // Second play reuses cache — no second full collect.
        session.playBlockingFinished = CompletableDeferred()
        session.play()
        session.playBlockingFinished.await()
        assertEquals(1, session.collectCount)
    }

    @Test
    fun play_streamsHotFlowWithoutFullCollect() = runRealTimeTest {
        val session = GatingSeekSession()
        val hot = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        session.load(AudioFlow(format, hot.asSharedFlow()))

        session.play()
        // playBlocking runs on Dispatchers.Default — use real time, not runTest virtual clock.
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(2_000) { session.playBlockingStarted.await() }
        }
        hot.emit(ByteArray(8) { 1 })
        hot.emit(ByteArray(8) { 2 })

        assertTrue(session.streamed)
        assertFalse(session.materialized)
        assertEquals(0, session.collectCount)
        assertEquals(SeekResult.Unsupported, session.seekTo(10.milliseconds))

        session.stop()
    }
}

/**
 * Mirrors the JVM/Web dual-path contract without opening a real audio device.
 */
private class GatingSeekSession : BaseAudioPlaybackSession() {
    var streamed = false
        private set
    var materialized = false
        private set
    var collectCount = 0
        private set

    @Volatile
    var playBlockingStarted = CompletableDeferred<Unit>()

    @Volatile
    var playBlockingFinished = CompletableDeferred<Unit>()

    private var pcm: ByteArray? = null

    override suspend fun preparePlayback(format: AudioFormat): AudioFormat = format

    override suspend fun playBlocking(audioFlow: AudioFlow) {
        playBlockingStarted.complete(Unit)
        try {
            if (audioFlow.isKnownFinite) {
                materialized = true
                val cached = pcm
                if (cached != null) return
                collectCount++
                var total = 0
                val chunks = ArrayList<ByteArray>()
                audioFlow.collect { chunk ->
                    chunks.add(chunk)
                    total += chunk.size
                }
                val out = ByteArray(total)
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(out, destinationOffset = offset)
                    offset += chunk.size
                }
                pcm = out
            } else {
                streamed = true
                audioFlow.collect { /* stream chunks; never materialize */ }
            }
        } finally {
            playBlockingFinished.complete(Unit)
        }
    }

    override fun onSeek(position: kotlin.time.Duration): SeekResult {
        if (pcm == null && audioFlow.value?.isKnownFinite != true) {
            return SeekResult.Unsupported
        }
        return SeekResult.Applied
    }

    override fun onPause() = Unit
    override fun onResume() = Unit
    override fun onStop() {
        pcm = null
    }
}
