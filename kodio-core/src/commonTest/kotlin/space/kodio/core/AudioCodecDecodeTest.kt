package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import space.kodio.core.io.decode

internal class AudioCodecDecodeTest {

    @Test
    fun `decode 16-bit signed LE mono samples`() {
        val chunk = byteArrayOf(
            0x00, 0x00, // 0
            0xFF.toByte(), 0x7F, // 32767
            0x00, 0x80.toByte(), // -32768
        )
        val format = fmtInt(
            rate = 48000,
            channels = Channels.Mono,
            depth = IntBitDepth.Sixteen,
            signed = true,
        )
        val decoded = decode(chunk, format)

        assertEquals(3, decoded.size)
        assertEquals(0.0, decoded[0].doubleValue(exactRequired = false), 1e-3)
        assertEquals(0.99997, decoded[1].doubleValue(exactRequired = false), 1e-3)
        assertEquals(-1.0, decoded[2].doubleValue(exactRequired = false), 1e-3)
    }

    @Test
    fun `decode Float32 LE mono samples`() {
        val chunk = createFloat32LeData(listOf(0.0f, 0.5f, -1.0f))
        val format = fmtFloat(48000, Channels.Mono, FloatPrecision.F32)
        val decoded = decode(chunk, format)

        assertEquals(3, decoded.size)
        assertEquals(0.0, decoded[0].doubleValue(exactRequired = false), 1e-3)
        assertEquals(0.5, decoded[1].doubleValue(exactRequired = false), 1e-3)
        assertEquals(-1.0, decoded[2].doubleValue(exactRequired = false), 1e-3)
    }

    private fun createFloat32LeData(samples: List<Float>): ByteArray {
        val result = ByteArray(samples.size * 4)
        var i = 0
        for (f in samples) {
            val bits = f.toRawBits()
            result[i++] = (bits and 0xFF).toByte()
            result[i++] = ((bits ushr 8) and 0xFF).toByte()
            result[i++] = ((bits ushr 16) and 0xFF).toByte()
            result[i++] = ((bits ushr 24) and 0xFF).toByte()
        }
        return result
    }
}
