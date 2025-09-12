package space.kodio.core

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import space.kodio.core.io.convertAudio

class AudioFlowFormatConversionTest {

    /* -------------------- Helpers -------------------- */

    private fun fmtInt(
        rate: Int,
        channels: Channels,
        depth: IntBitDepth,
        signed: Boolean = true,
        endianness: Endianness = Endianness.Little
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmInt(
            bitDepth = depth,
            endianness = endianness,
            layout = SampleLayout.Interleaved,
            signed = signed,
            packed = true
        )
    )

    private fun fmtInt16(rate: Int, channels: Channels, signed: Boolean = true, end: Endianness = Endianness.Little) =
        fmtInt(rate, channels, IntBitDepth.Sixteen, signed, end)

    private fun fmtInt8(rate: Int, channels: Channels, signed: Boolean = true, end: Endianness = Endianness.Little) =
        fmtInt(rate, channels, IntBitDepth.Eight, signed, end)

    // Helper: 16-bit LE from Short values
    private fun create16BitData(samples: List<Short>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var i = 0
        for (s in samples) {
            val v = s.toInt()
            result[i++] = (v and 0xFF).toByte()
            result[i++] = ((v ushr 8) and 0xFF).toByte()
        }
        return result
    }

    // Helper: 16-bit BE from Short values
    private fun create16BitDataBE(samples: List<Short>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var i = 0
        for (s in samples) {
            val v = s.toInt()
            result[i++] = ((v ushr 8) and 0xFF).toByte()
            result[i++] = (v and 0xFF).toByte()
        }
        return result
    }

    // Helper: 16-bit unsigned LE from Int values in [0, 65535]
    private fun create16BitUnsignedData(samples: List<Int>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var i = 0
        for (v in samples) {
            result[i++] = (v and 0xFF).toByte()
            result[i++] = ((v ushr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun create8BitData(samples: List<Byte>): ByteArray =
        samples.toByteArray()

    /* -------------------- Tests -------------------- */

    @Test
    fun `Convert 44100Hz 16bit Mono to 44100Hz 16bit Stereo`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt16(44100, Channels.Stereo)

        val inData = create16BitData(listOf(1000, -1000))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitData(listOf(1000, 1000, -1000, -1000))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz 16bit Stereo to 44100Hz 16bit Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Stereo)
        val outFormat = fmtInt16(44100, Channels.Mono)

        val inData = create16BitData(listOf(1000, 2000, -1000, -2000)) // L/R interleaved
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitData(listOf(1500, -1500)) // average per frame
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz 16bit Mono to 44100Hz 8bit Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt8(44100, Channels.Mono)

        // 16384 (16-bit) -> 0.5 -> 64 (8-bit)
        val inData = create16BitData(listOf(16384, -16384))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create8BitData(listOf(64, (-64).toByte()))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz 8bit Mono to 44100Hz 16bit Stereo`() = runTest {
        val inFormat = fmtInt8(44100, Channels.Mono)
        val outFormat = fmtInt16(44100, Channels.Stereo)

        // 64 (8-bit) -> 0.5 -> 16384 (16-bit)
        val inData = create8BitData(listOf(64, (-64).toByte()))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitData(listOf(16384, 16384, -16384, -16384))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert full-scale 16-bit stereo to 8-bit mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Stereo)
        val outFormat = fmtInt8(44100, Channels.Mono)

        val inData = create16BitData(listOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create8BitData(listOf(Byte.MAX_VALUE, Byte.MIN_VALUE))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `No conversion should return same flow instance`() {
        val format = fmtInt16(44100, Channels.Mono)
        val inData = byteArrayOf(1, 2, 3)
        val inFlow = AudioFlow(format, flowOf(inData))

        val outFlow = inFlow.convertAudio(format)

        assertSame(inFlow, outFlow)
    }

    @Test
    fun `Convert 16bit LE to 16bit BE`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono, signed = true, end = Endianness.Little)
        val outFormat = fmtInt16(44100, Channels.Mono, signed = true, end = Endianness.Big)

        val inData = create16BitData(listOf(1000, -1000))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitDataBE(listOf(1000, -1000))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 16bit signed to 16bit unsigned`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono, signed = true)
        val outFormat = fmtInt16(44100, Channels.Mono, signed = false)

        val inData = create16BitData(listOf(0, 16384, -16384))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // signed -> unsigned mapping (+32768 offset): [0, 16384, -16384] -> [32768, 49152, 16384]
        val expectedData = create16BitUnsignedData(listOf(32768, 49152, 16384))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 16bit unsigned to 16bit signed`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono, signed = false)
        val outFormat = fmtInt16(44100, Channels.Mono, signed = true)

        val inData = create16BitUnsignedData(listOf(32768, 49152, 16384))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitData(listOf(0, 16384, -16384))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Upsample 22050Hz Mono to 44100Hz Mono`() = runTest {
        val inFormat = fmtInt16(22050, Channels.Mono)
        val outFormat = fmtInt16(44100, Channels.Mono)

        val inData = create16BitData(listOf(10000, 20000))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)
        val result = outFlow.toList().first()

        assertEquals(outFormat, outFlow.format)

        // Expected with linear interpolation:
        // s1, (s1+s2)/2, s2, s2 (duplicate last for boundary)
        val expectedData = create16BitData(listOf(10000, 15000, 20000, 20000))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Downsample 44100Hz Mono to 22050Hz Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt16(22050, Channels.Mono)

        val inData = create16BitData(listOf(1000, 2000, 3000, 4000))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)
        val result = outFlow.toList().first()

        assertEquals(outFormat, outFlow.format)

        // With simple linear-decimate, we effectively pick every ~2nd sample:
        val expectedData = create16BitData(listOf(1000, 3000))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz 16bit Stereo to 22050Hz 8bit Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Stereo)
        val outFormat = fmtInt8(22050, Channels.Mono)

        val inData = create16BitData(listOf(16384, 8192, -16384, -8192)) // L/R, L/R
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)
        val result = outFlow.toList().first()

        assertEquals(outFormat, outFlow.format)

        // Decode: [0.5, 0.25, -0.5, -0.25]
        // Resample to 22050: L-> [0.5], R-> [0.25]  => interleave: [0.5, 0.25]
        // Downmix to mono: (0.5+0.25)/2 = 0.375
        // Encode 8-bit signed: 0.375 * 128 = 48
        val expectedData = create8BitData(listOf(48.toByte()))
        assertContentEquals(expectedData, result)
    }
}