package space.kodio.core.io.files.au

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.*
import space.kodio.core.io.AudioSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAuTest {

    private fun fmtInt(
        rate: Int,
        channels: Channels,
        depth: IntBitDepth,
        signed: Boolean = true,
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmInt(
            bitDepth = depth,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = signed,
            packed = true
        )
    )

    private fun fmtFloat(
        rate: Int,
        channels: Channels,
        precision: FloatPrecision,
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmFloat(
            precision = precision,
            layout = SampleLayout.Interleaved
        )
    )

    private fun rampBytes(size: Int): ByteArray =
        ByteArray(size) { i -> (i % 251).toByte() }

    private fun encodeAu(format: AudioFormat, pcmData: ByteArray): ByteArray {
        val buf = Buffer()
        writeAu(AudioSource.of(format, *pcmData), buf)
        return buf.readByteArray()
    }

    private fun assertAuRoundTrip(format: AudioFormat, pcmData: ByteArray) {
        val auBytes = encodeAu(format, pcmData)
        val decoded = readAu(Buffer().apply { write(auBytes) })

        assertEquals(format.sampleRate, decoded.format.sampleRate, "sampleRate mismatch")
        assertEquals(format.channels, decoded.format.channels, "channels mismatch")
        assertEquals(format.encoding, decoded.format.encoding, "encoding mismatch")

        val decodedPayload = decoded.source.readByteArray()
        assertTrue(
            pcmData.contentEquals(decodedPayload),
            "Payload mismatch: expected ${pcmData.size} bytes, got ${decodedPayload.size} bytes"
        )
    }

    @Test
    fun roundTrip16BitStereo44100Hz() {
        val format = fmtInt(44100, Channels.Stereo, IntBitDepth.Sixteen)
        val pcm = rampBytes(44100 * 4)
        assertAuRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip8BitSignedMono() {
        val format = fmtInt(22050, Channels.Mono, IntBitDepth.Eight, signed = true)
        val pcm = rampBytes(22050)
        assertAuRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip24BitMono() {
        val format = fmtInt(48000, Channels.Mono, IntBitDepth.TwentyFour)
        val pcm = rampBytes(48000 * 3)
        assertAuRoundTrip(format, pcm)
    }

    @Test
    fun roundTripFloat32Stereo() {
        val format = fmtFloat(48000, Channels.Stereo, FloatPrecision.F32)
        val pcm = rampBytes(48000 * 2 * 4)
        assertAuRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip32BitIntMono() {
        val format = fmtInt(8000, Channels.Mono, IntBitDepth.ThirtyTwo)
        val pcm = rampBytes(8000 * 4)
        assertAuRoundTrip(format, pcm)
    }

    @Test
    fun roundTripFloat64Mono() {
        val format = fmtFloat(8000, Channels.Mono, FloatPrecision.F64)
        val pcm = rampBytes(8000 * 8)
        assertAuRoundTrip(format, pcm)
    }
}
