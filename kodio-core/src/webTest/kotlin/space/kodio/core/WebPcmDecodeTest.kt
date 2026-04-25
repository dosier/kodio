package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Round-trip / layout coverage for the helpers backing the web playback path.
 *
 * The actual `createBufferFrom` -> Web Audio integration can only be exercised
 * inside a browser, but the byte/channel reshaping happens entirely on the
 * Kotlin side and is fully testable here.
 */
class WebPcmDecodeTest {

    @Test
    fun toFloatArrayLE_decodes_known_floats() {
        val samples = floatArrayOf(0.0f, 1.0f, -1.0f, 0.5f, -0.25f, Float.MIN_VALUE, Float.MAX_VALUE)
        val bytes = samples.toLittleEndianBytes()
        val decoded = bytes.toFloatArrayLE()
        assertContentEquals(samples, decoded)
    }

    @Test
    fun toFloatArrayLE_rejects_unaligned_input() {
        assertFailsWith<IllegalArgumentException> {
            byteArrayOf(0x00, 0x01, 0x02).toFloatArrayLE()
        }
    }

    @Test
    fun deinterleave_mono_returns_input_without_copy() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val channels = input.deinterleave(channels = 1)
        assertEquals(1, channels.size)
        assertSame(input, channels[0])
    }

    @Test
    fun deinterleave_stereo_splits_alternating_samples() {
        val input = floatArrayOf(
            -1.0f, 1.0f,
            -0.5f, 0.5f,
            -0.25f, 0.25f,
        )
        val channels = input.deinterleave(channels = 2)
        assertEquals(2, channels.size)
        assertContentEquals(floatArrayOf(-1.0f, -0.5f, -0.25f), channels[0])
        assertContentEquals(floatArrayOf(1.0f, 0.5f, 0.25f), channels[1])
    }

    @Test
    fun deinterleave_three_channels_round_robin() {
        val input = floatArrayOf(
            0.0f, 0.1f, 0.2f,
            1.0f, 1.1f, 1.2f,
        )
        val channels = input.deinterleave(channels = 3)
        assertEquals(3, channels.size)
        assertContentEquals(floatArrayOf(0.0f, 1.0f), channels[0])
        assertContentEquals(floatArrayOf(0.1f, 1.1f), channels[1])
        assertContentEquals(floatArrayOf(0.2f, 1.2f), channels[2])
    }

    @Test
    fun deinterleave_rejects_misaligned_buffer() {
        val input = floatArrayOf(0.0f, 0.1f, 0.2f)
        assertFailsWith<IllegalArgumentException> { input.deinterleave(channels = 2) }
    }

    @Test
    fun deinterleave_rejects_non_positive_channel_count() {
        assertFailsWith<IllegalArgumentException> { floatArrayOf().deinterleave(channels = 0) }
    }

    private fun FloatArray.toLittleEndianBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val bits = this[i].toRawBits()
            val base = i * 4
            out[base] = (bits and 0xFF).toByte()
            out[base + 1] = ((bits ushr 8) and 0xFF).toByte()
            out[base + 2] = ((bits ushr 16) and 0xFF).toByte()
            out[base + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return out
    }
}
