package space.kodio.core

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import space.kodio.core.io.convertAudio

/**
 * Tests for AudioFlow format conversion.
 * 
 * TODO: Fix native dependency issues on Linux CI.
 * See: https://github.com/dosier/kodio/issues/15
 */
// @Ignore // Skipped on CI - native dependency loading issues on Linux. See #15
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

    private fun fmtInt24(rate: Int, channels: Channels, signed: Boolean = true, end: Endianness = Endianness.Little) =
        fmtInt(rate, channels, IntBitDepth.TwentyFour, signed, end)

    private fun fmtInt32(rate: Int, channels: Channels, signed: Boolean = true, end: Endianness = Endianness.Little) =
        fmtInt(rate, channels, IntBitDepth.ThirtyTwo, signed, end)

    private fun fmtFloat32(rate: Int, channels: Channels) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
    )

    private fun create8BitData(samples: List<Byte>): ByteArray =
        samples.toByteArray()

    private fun create24BitData(samples: List<Int>): ByteArray {
        val result = ByteArray(samples.size * 3)
        var i = 0
        for (v in samples) {
            val u = v and 0xFFFFFF
            result[i++] = (u and 0xFF).toByte()
            result[i++] = ((u ushr 8) and 0xFF).toByte()
            result[i++] = ((u ushr 16) and 0xFF).toByte()
        }
        return result
    }

    private fun create32BitData(samples: List<Int>): ByteArray {
        val result = ByteArray(samples.size * 4)
        var i = 0
        for (v in samples) {
            result[i++] = (v and 0xFF).toByte()
            result[i++] = ((v ushr 8) and 0xFF).toByte()
            result[i++] = ((v ushr 16) and 0xFF).toByte()
            result[i++] = ((v ushr 24) and 0xFF).toByte()
        }
        return result
    }

    private fun createFloat32Data(samples: List<Float>): ByteArray {
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

    private fun read24BitSamples(data: ByteArray): List<Int> {
        val samples = mutableListOf<Int>()
        var i = 0
        while (i + 2 < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val b1 = data[i++].toInt() and 0xFF
            val b2 = data[i++].toInt() and 0xFF
            var u = (b2 shl 16) or (b1 shl 8) or b0
            if (u and 0x800000 != 0) u = u or -0x1000000 // sign-extend
            samples.add(u)
        }
        return samples
    }

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

    /* -------------------- 24-bit Tests -------------------- */

    @Test
    fun `Convert 16bit Mono to 24bit Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt24(44100, Channels.Mono)

        // 16-bit value V should become V * 256 in 24-bit
        val inData = create16BitData(listOf(1000, -1000, 0))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val resultSamples = read24BitSamples(result)
        assertEquals(3, resultSamples.size, "Expected 3 samples")
        assertEquals(256000, resultSamples[0], "1000 * 256 = 256000")
        assertEquals(-256000, resultSamples[1], "-1000 * 256 = -256000")
        assertEquals(0, resultSamples[2], "0 * 256 = 0")
    }

    @Test
    fun `Convert 16bit Mono to 24bit Mono - full scale`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt24(44100, Channels.Mono)

        // 32767 -> 32767*256 = 8388352, -32768 -> -32768*256 = -8388608
        val inData = create16BitData(listOf(Short.MAX_VALUE, Short.MIN_VALUE))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        val resultSamples = read24BitSamples(result)
        assertEquals(2, resultSamples.size)
        assertEquals(8388352, resultSamples[0], "Short.MAX_VALUE * 256")
        assertEquals(-8388608, resultSamples[1], "Short.MIN_VALUE * 256")
    }

    @Test
    fun `Convert 24bit Mono to 16bit Mono`() = runTest {
        val inFormat = fmtInt24(44100, Channels.Mono)
        val outFormat = fmtInt16(44100, Channels.Mono)

        // 256000 (24-bit) -> normalized 256000/8388608 -> 16-bit: ~1000
        val inData = create24BitData(listOf(256000, -256000, 0))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)
        assertEquals(6, result.size, "3 samples * 2 bytes")

        val expectedData = create16BitData(listOf(1000, -1000, 0))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 16bit Stereo to 24bit Stereo`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Stereo)
        val outFormat = fmtInt24(44100, Channels.Stereo)

        val inData = create16BitData(listOf(5000, -5000, 10000, -10000))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        val resultSamples = read24BitSamples(result)
        assertEquals(4, resultSamples.size)
        assertEquals(1280000, resultSamples[0], "5000 * 256")
        assertEquals(-1280000, resultSamples[1], "-5000 * 256")
        assertEquals(2560000, resultSamples[2], "10000 * 256")
        assertEquals(-2560000, resultSamples[3], "-10000 * 256")
    }

    @Test
    fun `Convert Float32 Mono to 24bit Mono`() = runTest {
        val inFormat = fmtFloat32(44100, Channels.Mono)
        val outFormat = fmtInt24(44100, Channels.Mono)

        // 0.5f -> 0.5 * 8388608 = 4194304, -0.5f -> -4194304
        val inData = createFloat32Data(listOf(0.5f, -0.5f, 0.0f))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        val resultSamples = read24BitSamples(result)
        assertEquals(3, resultSamples.size)
        assertEquals(4194304, resultSamples[0], "0.5 * 2^23")
        assertEquals(-4194304, resultSamples[1], "-0.5 * 2^23")
        assertEquals(0, resultSamples[2], "0.0 * 2^23")
    }

    @Test
    fun `Convert 16bit Mono to 32bit Mono`() = runTest {
        val inFormat = fmtInt16(44100, Channels.Mono)
        val outFormat = fmtInt32(44100, Channels.Mono)

        // 16-bit value V -> normalized V/32768 -> 32-bit: V * 65536
        val inData = create16BitData(listOf(1000, -1000, 0))
        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)
        assertEquals(12, result.size, "3 samples * 4 bytes")

        val expectedData = create32BitData(listOf(65536000, -65536000, 0))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `24bit round-trip preserves values`() = runTest {
        val fmt24 = fmtInt24(44100, Channels.Mono)

        val original = create24BitData(listOf(100000, -100000, 0, 4194304, -4194304))
        val flow = AudioFlow(fmt24, flowOf(original))

        // 24-bit -> 24-bit should be identity (same format)
        val outFlow = flow.convertAudio(fmt24)
        assertSame(flow, outFlow, "Same format should return same flow")
    }

    @Test
    fun `16bit to 24bit to 16bit round-trip`() = runTest {
        val fmt16 = fmtInt16(44100, Channels.Mono)
        val fmt24 = fmtInt24(44100, Channels.Mono)

        val originalSamples: List<Short> = listOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE, 12345, -12345)
        val inData = create16BitData(originalSamples)
        val inFlow = AudioFlow(fmt16, flowOf(inData))

        // Convert 16 -> 24 -> 16
        val to24 = inFlow.convertAudio(fmt24)
        val backTo16 = to24.convertAudio(fmt16)

        val result = backTo16.toList().first()
        val expectedData = create16BitData(originalSamples)
        assertContentEquals(expectedData, result, "16->24->16 round-trip should be lossless")
    }
}