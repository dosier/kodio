package space.kodio.ktor

import space.kodio.core.AudioFormat
import space.kodio.core.AudioQuality
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import space.kodio.core.io.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pure-codec tests that don't need a transport — keeps the wire format
 * itself covered on every target including JS/Wasm.
 */
class AudioFlowHeaderCodecTest {

    @Test
    fun `round-trip preserves preset format`() {
        val format = AudioQuality.High.format
        val bytes = encodeFormatHeader(format)
        assertEquals(format, decodeFormatHeader(bytes))
    }

    @Test
    fun `round-trip preserves a custom 24-bit BE PcmInt format`() {
        val format = AudioFormat(
            sampleRate = 96_000,
            channels = Channels.Stereo,
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Big,
                layout = SampleLayout.Interleaved,
                signed = true,
                packed = true,
            ),
        )
        val bytes = encodeFormatHeader(format)
        assertEquals(format, decodeFormatHeader(bytes))
    }

    @Test
    fun `header starts with the wire format magic`() {
        val format = AudioQuality.Voice.format
        val bytes = encodeFormatHeader(format)
        val magic = AudioFlowWireFormat.MAGIC.encodeToByteArray()
        assertTrue(bytes.size >= magic.size)
        for (i in magic.indices) assertEquals(magic[i], bytes[i])
    }

    @Test
    fun `decode rejects payload missing magic`() {
        // Raw format payload without the KDIO magic prefix should be rejected.
        val rawPayload = AudioQuality.Voice.format.encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            decodeFormatHeader(rawPayload)
        }
    }
}
