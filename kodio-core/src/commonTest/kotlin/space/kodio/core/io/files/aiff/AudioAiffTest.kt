package space.kodio.core.io.files.aiff

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.*
import space.kodio.core.io.AudioSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAiffTest {

    private fun encodeAiff(format: AudioFormat, pcmData: ByteArray): ByteArray {
        val buf = Buffer()
        writeAiff(AudioSource.of(format, *pcmData), buf)
        return buf.readByteArray()
    }

    private fun assertAiffRoundTrip(format: AudioFormat, pcmData: ByteArray) {
        val aiffBytes = encodeAiff(format, pcmData)
        val decoded = readAiff(Buffer().apply { write(aiffBytes) })

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
        assertAiffRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip8BitSignedMono22050Hz() {
        val format = fmtInt(22050, Channels.Mono, IntBitDepth.Eight, signed = true)
        val pcm = rampBytes(22050)
        assertAiffRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip24BitStereo48000Hz() {
        val format = fmtInt(48000, Channels.Stereo, IntBitDepth.TwentyFour)
        val pcm = rampBytes(48000 * 2 * 3)
        assertAiffRoundTrip(format, pcm)
    }

    @Test
    fun roundTrip32BitMono96000Hz() {
        val format = fmtInt(96000, Channels.Mono, IntBitDepth.ThirtyTwo)
        val pcm = rampBytes(96000 * 4)
        assertAiffRoundTrip(format, pcm)
    }

    @Test
    fun roundTripFloat32Mono48000Hz() {
        val format = fmtFloat(48000, Channels.Mono, FloatPrecision.F32)
        val pcm = rampBytes(48000 * 4)
        assertAiffRoundTrip(format, pcm)
    }

    @Test
    fun roundTripFloat64Stereo48000Hz() {
        val format = fmtFloat(48000, Channels.Stereo, FloatPrecision.F64)
        val pcm = rampBytes(48000 * 2 * 8)
        assertAiffRoundTrip(format, pcm)
    }
}
