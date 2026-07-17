package space.kodio.compose

import space.kodio.compose.internal.calculateRmsAmplitude
import space.kodio.compose.internal.sampleAbsoluteAmplitude
import space.kodio.core.AudioFormat
import space.kodio.core.AudioQuality
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.FloatPrecision
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmAmplitudeTest {

    @Test
    fun sampleAbsoluteAmplitude_readsSignedPcm16Le() {
        val encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        // +32767 → near 1.0
        val maxBytes = byteArrayOf(0xFF.toByte(), 0x7F)
        assertTrue(sampleAbsoluteAmplitude(maxBytes, 0, encoding) > 0.99f)

        // 0
        val zero = byteArrayOf(0x00, 0x00)
        assertEquals(0f, sampleAbsoluteAmplitude(zero, 0, encoding))
    }

    @Test
    fun sampleAbsoluteAmplitude_readsPcm24Le() {
        val encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.TwentyFour,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        )
        // +8388607 (0x7FFFFF) → near 1.0
        val maxBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F)
        assertTrue(sampleAbsoluteAmplitude(maxBytes, 0, encoding) > 0.99f)
    }

    @Test
    fun sampleAbsoluteAmplitude_readsFloat32() {
        val encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        val bits = 0.5f.toBits()
        val bytes = byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits ushr 8) and 0xFF).toByte(),
            ((bits ushr 16) and 0xFF).toByte(),
            ((bits ushr 24) and 0xFF).toByte(),
        )
        val amp = sampleAbsoluteAmplitude(bytes, 0, encoding)
        assertTrue(kotlin.math.abs(amp - 0.5f) < 1e-6f, "expected ~0.5, got $amp")
    }

    @Test
    fun calculateRmsAmplitude_floatIsNotSilent() {
        val format = AudioFormat(
            sampleRate = 48_000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved),
        )
        val bits = 0.25f.toBits()
        val sample = byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits ushr 8) and 0xFF).toByte(),
            ((bits ushr 16) and 0xFF).toByte(),
            ((bits ushr 24) and 0xFF).toByte(),
        )
        val chunk = sample + sample + sample + sample
        val rms = calculateRmsAmplitude(chunk, format)
        assertTrue(rms > 0.2f, "expected non-trivial RMS for float PCM, got $rms")
    }

    @Test
    fun waveformPeaks_handlesLosslessInt24() {
        val format = AudioQuality.Lossless.format
        // Build a few loud frames of silence-then-peak pattern
        val bytesPerFrame = format.bytesPerFrame
        val frames = 32
        val data = ByteArray(frames * bytesPerFrame)
        // Put a near-full-scale sample in the middle frame (LE int24/32 depending on quality)
        val mid = (frames / 2) * bytesPerFrame
        when (val enc = format.encoding) {
            is SampleEncoding.PcmInt -> {
                val bps = enc.bitDepth.bits / 8
                // positive full-ish scale: set high byte to 0x7F
                data[mid + bps - 1] = 0x7F
            }
            is SampleEncoding.PcmFloat -> {
                val bits = 1f.toBits()
                data[mid] = (bits and 0xFF).toByte()
                data[mid + 1] = ((bits ushr 8) and 0xFF).toByte()
                data[mid + 2] = ((bits ushr 16) and 0xFF).toByte()
                data[mid + 3] = ((bits ushr 24) and 0xFF).toByte()
            }
        }
        val recording = AudioRecording.fromPcmBytes(format, data)
        val peaks = recording.waveformPeaks(barCount = 8)
        assertEquals(8, peaks.size)
        assertTrue(peaks.any { it > 0.5f }, "expected a visible peak for lossless PCM")
    }
}
