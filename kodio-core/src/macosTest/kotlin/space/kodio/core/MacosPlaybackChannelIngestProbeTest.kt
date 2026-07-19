package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import space.kodio.core.io.convertAudio
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Simulates the Panama begin/append/end + play path against MacosAudioPlaybackSession.
 */
@OptIn(ExperimentalForeignApi::class)
class MacosPlaybackChannelIngestProbeTest {

    @Test
    fun formatRoundTripFloat96k() {
        val fmt = AudioFormat(
            96000, Channels.Stereo,
            SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        val bytes = fmt.encodeToByteArray()
        val decoded = bytes.decodeAsAudioFormat()
        assertEquals(fmt, decoded)
        println("roundTrip bytes=${bytes.toList()} decoded=$decoded")
    }

    @Test
    fun channelIngestF32_96k_likePanama() {
        runBlocking {
        val srcFmt = AudioFormat(96000, Channels.Stereo, SampleEncoding.PcmInt(IntBitDepth.TwentyFour))
        // Build ~0.2s of PCM24
        val frames = (96000 * 0.2).toInt()
        val pcm24 = ByteArray(frames * 6)
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * f / 96000.0) * 4000000).toInt() and 0xFFFFFF
            val o = f * 6
            val b0 = (s and 0xFF).toByte()
            val b1 = ((s ushr 8) and 0xFF).toByte()
            val b2 = ((s ushr 16) and 0xFF).toByte()
            pcm24[o] = b0; pcm24[o+1] = b1; pcm24[o+2] = b2
            pcm24[o+3] = b0; pcm24[o+4] = b1; pcm24[o+5] = b2
        }
        val f32Flow = AudioFlow(srcFmt, kotlinx.coroutines.flow.flowOf(pcm24), pcm24.size.toLong())
            .convertAudio(
                AudioFormat(96000, Channels.Stereo, SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved))
            )
        val f32Bytes = ArrayList<ByteArray>()
        f32Flow.collect { f32Bytes.add(it) }
        val total = f32Bytes.sumOf { it.size }
        println("converted chunks=${f32Bytes.size} totalBytes=$total known=${f32Flow.knownByteCount}")

        val channel = Channel<ByteArray>(capacity = 64)
        val session = MacosAudioPlaybackSession()
        val audioFlow = AudioFlow(
            format = f32Flow.format,
            data = channel.receiveAsFlow(),
            knownByteCount = f32Flow.knownByteCount,
        )
        session.load(audioFlow)

        val producer = launch {
            for (chunk in f32Bytes) {
                channel.send(chunk)
            }
            channel.close()
            println("producer closed channel")
        }

        session.play()
        val terminal = session.state.first {
            it is AudioPlaybackSession.State.Finished ||
                it is AudioPlaybackSession.State.Error ||
                it is AudioPlaybackSession.State.Idle
        }
        println("channelIngest terminal=$terminal pos=${session.position.value}")
        producer.join()
        session.stop()
        assertIs<AudioPlaybackSession.State.Finished>(terminal)
        }
    }

    @Test
    fun channelIngest_producerBeforePlay_closesEarly() {
        runBlocking {
        // Reproduces race: JVM finishes append+end before native play collects
        val fmt = AudioFormat(
            96000, Channels.Stereo,
            SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        val frames = 9600
        val pcm = ByteArray(frames * 8) // 0.1s silence/noise
        for (i in pcm.indices) pcm[i] = (i % 17).toByte()

        val channel = Channel<ByteArray>(capacity = 64)
        val session = MacosAudioPlaybackSession()
        session.load(AudioFlow(fmt, channel.receiveAsFlow(), pcm.size.toLong()))

        // Feed everything before play (Panama can do this for small recordings)
        channel.send(pcm)
        channel.close()

        session.play()
        val terminal = session.state.first {
            it is AudioPlaybackSession.State.Finished ||
                it is AudioPlaybackSession.State.Error ||
                it is AudioPlaybackSession.State.Idle
        }
        println("earlyClose terminal=$terminal pos=${session.position.value}")
        delay(20.milliseconds)
        session.stop()
        assertIs<AudioPlaybackSession.State.Finished>(terminal)
        }
    }
}
