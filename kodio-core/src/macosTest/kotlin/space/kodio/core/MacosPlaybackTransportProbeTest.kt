package space.kodio.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
class MacosPlaybackTransportProbeTest {

    private fun sinePcm16Stereo(sampleRate: Int, seconds: Double, freq: Double = 440.0): ByteArray {
        val frames = (sampleRate * seconds).toInt()
        val out = ByteArray(frames * 4)
        var i = 0
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * freq * f / sampleRate) * 16000).toInt().toShort()
            val b0 = (s.toInt() and 0xFF).toByte()
            val b1 = ((s.toInt() ushr 8) and 0xFF).toByte()
            out[i++] = b0; out[i++] = b1; out[i++] = b0; out[i++] = b1
        }
        return out
    }

    private fun sinePcm24Stereo(sampleRate: Int, seconds: Double, freq: Double = 440.0): ByteArray {
        val frames = (sampleRate * seconds).toInt()
        val out = ByteArray(frames * 6)
        var i = 0
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * freq * f / sampleRate) * 4000000).toInt()
            val u = s and 0xFFFFFF
            val b0 = (u and 0xFF).toByte()
            val b1 = ((u ushr 8) and 0xFF).toByte()
            val b2 = ((u ushr 16) and 0xFF).toByte()
            out[i++] = b0; out[i++] = b1; out[i++] = b2
            out[i++] = b0; out[i++] = b1; out[i++] = b2
        }
        return out
    }

    private fun sineF32Stereo(sampleRate: Int, seconds: Double, freq: Double = 440.0): ByteArray {
        val frames = (sampleRate * seconds).toInt()
        val out = ByteArray(frames * 8)
        var i = 0
        for (f in 0 until frames) {
            val v = kotlin.math.sin(2.0 * kotlin.math.PI * freq * f / sampleRate).toFloat()
            val bits = v.toRawBits()
            val b0 = (bits and 0xFF).toByte()
            val b1 = ((bits ushr 8) and 0xFF).toByte()
            val b2 = ((bits ushr 16) and 0xFF).toByte()
            val b3 = ((bits ushr 24) and 0xFF).toByte()
            repeat(2) {
                out[i++] = b0; out[i++] = b1; out[i++] = b2; out[i++] = b3
            }
        }
        return out
    }

    private fun playUntilTerminal(format: AudioFormat, pcm: ByteArray): AudioPlaybackSession.State =
        runBlocking {
            val session = MacosAudioPlaybackSession()
            val flow = AudioFlow(format, flowOf(pcm), knownByteCount = pcm.size.toLong())
            session.load(flow)
            session.play()
            val terminal = session.state.first {
                it is AudioPlaybackSession.State.Finished ||
                    it is AudioPlaybackSession.State.Error ||
                    it is AudioPlaybackSession.State.Idle
            }
            // Allow a little drain
            delay(50.milliseconds)
            println("playUntilTerminal format=$format -> $terminal pos=${session.position.value}")
            session.stop()
            terminal
        }

    @Test
    fun playPcm16Stereo44k() {
        val fmt = AudioFormat(44100, Channels.Stereo, SampleEncoding.PcmInt(IntBitDepth.Sixteen))
        val st = playUntilTerminal(fmt, sinePcm16Stereo(44100, 0.3))
        assertIs<AudioPlaybackSession.State.Finished>(st)
    }

    @Test
    fun playPcm16Mono44k_upmix() {
        val fmt = AudioFormat(44100, Channels.Mono, SampleEncoding.PcmInt(IntBitDepth.Sixteen))
        val frames = (44100 * 0.3).toInt()
        val pcm = ByteArray(frames * 2)
        for (f in 0 until frames) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * f / 44100.0) * 16000).toInt()
            pcm[f * 2] = (s and 0xFF).toByte()
            pcm[f * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        val st = playUntilTerminal(fmt, pcm)
        assertIs<AudioPlaybackSession.State.Finished>(st)
    }

    @Test
    fun playF32Stereo96k() {
        val fmt = AudioFormat(
            96000, Channels.Stereo,
            SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        val st = playUntilTerminal(fmt, sineF32Stereo(96000, 0.25))
        println("F32 result=$st")
        assertTrue(
            st is AudioPlaybackSession.State.Finished || st is AudioPlaybackSession.State.Error,
            "unexpected $st"
        )
        assertIs<AudioPlaybackSession.State.Finished>(st)
    }

    @Test
    fun playPcm24Stereo96k() {
        val fmt = AudioFormat(96000, Channels.Stereo, SampleEncoding.PcmInt(IntBitDepth.TwentyFour))
        val st = playUntilTerminal(fmt, sinePcm24Stereo(96000, 0.25))
        println("PCM24 result=$st")
        assertIs<AudioPlaybackSession.State.Finished>(st)
    }
}
