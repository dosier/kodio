package space.kodio.core.io.files.aiff

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.*
import space.kodio.core.io.AudioSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAiffTest {

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

    private fun rampBytes(size: Int): ByteArray =
        ByteArray(size) { i -> (i % 251).toByte() }

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
    fun roundTrip16BitMono48000Hz() {
        val format = fmtInt(48000, Channels.Mono, IntBitDepth.Sixteen)
        val pcm = rampBytes(48000 * 2)
        assertAiffRoundTrip(format, pcm)
    }
}
