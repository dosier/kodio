import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AudioFlowFormatConversionTest {

    // Helper function to create 16-bit little-endian byte arrays from Short values
    private fun create16BitData(samples: List<Short>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var index = 0
        for (sample in samples) {
            val value = sample.toInt()
            // Little-endian: LSB first, then MSB
            result[index++] = (value and 0xFF).toByte()
            result[index++] = (value shr 8 and 0xFF).toByte()
        }
        return result
    }

    // Helper function to create 16-bit big-endian byte arrays from Short values
    private fun create16BitDataBE(samples: List<Short>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var index = 0
        for (sample in samples) {
            val value = sample.toInt()
            // Big-endian: MSB first, then LSB
            result[index++] = (value shr 8 and 0xFF).toByte()
            result[index++] = (value and 0xFF).toByte()
        }
        return result
    }

    // Helper function to create 16-bit unsigned little-endian byte arrays
    private fun create16BitUnsignedData(samples: List<Int>): ByteArray {
        val result = ByteArray(samples.size * 2)
        var index = 0
        for (sample in samples) {
            result[index++] = (sample and 0xFF).toByte()
            result[index++] = (sample shr 8 and 0xFF).toByte()
        }
        return result
    }

    // Helper function to create 8-bit byte arrays from Byte values
    private fun create8BitData(samples: List<Byte>): ByteArray {
        return samples.toByteArray()
    }

    @Test
    fun `Convert 44100Hz, 16bit, Mono to 44100Hz, 16bit, Stereo`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Stereo, Encoding.Pcm.Signed, Endianness.Little)

        val inData = create16BitData(listOf(1000, -1000))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // Mono samples are duplicated for each channel in stereo
        val expectedData = create16BitData(listOf(1000, 1000, -1000, -1000))

        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz, 16bit, Stereo to 44100Hz, 16bit, Mono`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Stereo, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)

        val inData = create16BitData(listOf(1000, 2000, -1000, -2000)) // Interleaved L/R

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // Stereo channels are averaged for mono
        val expectedData = create16BitData(listOf(1500, -1500))

        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz, 16bit, Mono to 44100Hz, 8bit, Mono`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Eight, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)

        // 16384 (16-bit) -> 0.5f -> 64 (8-bit)
        val inData = create16BitData(listOf(16384, -16384))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // Expected 8-bit values are scaled down
        val expectedData = create8BitData(listOf(64, -64))

        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 44100Hz, 8bit, Mono to 44100Hz, 16bit, Stereo`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Eight, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Stereo, Encoding.Pcm.Signed, Endianness.Little)

        // 64 (8-bit) -> 0.5f -> 16384 (16-bit)
        val inData = create8BitData(listOf(64, -64))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // 8-bit values are scaled up to 16-bit and duplicated for stereo.
        val expectedData = create16BitData(listOf(16384, 16384, -16384, -16384))

        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert full-scale 16-bit stereo to 8-bit mono`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Stereo, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Eight, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)

        // Use max and min values to test clipping and scaling boundaries
        val inData = create16BitData(listOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)

        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        // Averaging MIN_VALUEs results in a value that scales to Byte.MIN_VALUE
        val expectedData = create8BitData(listOf(Byte.MAX_VALUE, Byte.MIN_VALUE))

        assertContentEquals(expectedData, result)
    }

    @Test
    fun `No conversion should return same flow instance`() {
        val format = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val inData = byteArrayOf(1, 2, 3)
        val inFlow = AudioFlow(format, flowOf(inData))

        val outFlow = inFlow.convertAudio(format)

        assertSame(inFlow, outFlow)
    }

    @Test
    fun `Unsupported sample rate conversion throws exception`() {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono)
        val outFormat = AudioFormat(22050, BitDepth.Sixteen, Channels.Mono)
        val inFlow = AudioFlow(inFormat, flowOf(byteArrayOf()))

        assertFailsWith<UnsupportedOperationException> {
            inFlow.convertAudio(outFormat)
        }
    }

    @Test
    fun `Unsupported encoding throws exception`() {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Unknown, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val inFlow = AudioFlow(inFormat, flowOf(byteArrayOf()))

        assertFailsWith<IllegalArgumentException> {
            inFlow.convertAudio(outFormat)
        }
    }

    @Test
    fun `Convert 16bit LE to 16bit BE`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Big)

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
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Unsigned, Endianness.Little)

        val inData = create16BitData(listOf(0, 16384, -16384))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)
        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitUnsignedData(listOf(32768, 49152, 16384))
        assertContentEquals(expectedData, result)
    }

    @Test
    fun `Convert 16bit unsigned to 16bit signed`() = runTest {
        val inFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Unsigned, Endianness.Little)
        val outFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono, Encoding.Pcm.Signed, Endianness.Little)

        val inData = create16BitUnsignedData(listOf(32768, 49152, 16384))

        val inFlow = AudioFlow(inFormat, flowOf(inData))
        val outFlow = inFlow.convertAudio(outFormat)
        val result = outFlow.toList().first()
        assertEquals(outFormat, outFlow.format)

        val expectedData = create16BitData(listOf(0, 16384, -16384))
        assertContentEquals(expectedData, result)
    }
}