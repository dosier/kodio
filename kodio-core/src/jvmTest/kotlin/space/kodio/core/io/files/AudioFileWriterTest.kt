package space.kodio.core.io.files

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readShortLe
import kotlinx.io.readString
import space.kodio.core.*
import space.kodio.core.fmtInt
import space.kodio.core.io.AudioSource
import kotlin.test.*

class AudioFileWriterTest {

    lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Path(SystemTemporaryDirectory, "kodio-test")
        SystemFileSystem.createDirectories(tempDir)
    }

    @Test
    fun `write with 16-bit stereo PCM format writes correct WAV header`() = runTest {
        val testPath = Path(tempDir.toString(), "test_16bit_stereo.wav")
        val format = fmtInt(
            rate = 44100,
            channels = Channels.Stereo,
            depth = IntBitDepth.Sixteen,
            signed = true,
            endianness = Endianness.Little,
        )
        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        writer.write(AudioSource.of(format, 0x12, 0x34, 0x56, 0x78))

        val buf = SystemFileSystem.source(testPath).buffered()

        val expectedSubChunk2Size = 4
        val expectedChunkSize = 36 + expectedSubChunk2Size
        val expectedNumChannels = 2
        val expectedBitsPerSample = 16
        val expectedSampleRate = 44100
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        assertEquals("RIFF", buf.readString(4))
        assertEquals(expectedChunkSize, buf.readIntLe())
        assertEquals("WAVE", buf.readString(4))

        assertEquals("fmt ", buf.readString(4))
        assertEquals(16, buf.readIntLe())
        assertEquals(1, buf.readShortLe().toInt())
        assertEquals(expectedNumChannels, buf.readShortLe().toInt())
        assertEquals(expectedSampleRate, buf.readIntLe())
        assertEquals(expectedByteRate, buf.readIntLe())
        assertEquals(expectedBlockAlign, buf.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, buf.readShortLe().toInt())

        assertEquals("data", buf.readString(4))
        assertEquals(expectedSubChunk2Size, buf.readIntLe())

        val payload = buf.readByteArray()
        assertTrue(payload.contentEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
    }

    @Test
    fun `write with 8-bit mono PCM format writes correct WAV header`() {
        val testPath = Path(tempDir.toString(), "test_8bit_mono.wav")
        val format = fmtInt(
            rate = 22050,
            channels = Channels.Mono,
            depth = IntBitDepth.Eight,
            signed = false,
            endianness = Endianness.Little,
        )

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        writer.write(AudioSource.of(format, 0xAB.toByte(), 0xCD.toByte()))

        val buf = SystemFileSystem.source(testPath).buffered()

        val expectedSubChunk2Size = 2
        val expectedNumChannels = 1
        val expectedBitsPerSample = 8
        val expectedSampleRate = 22050
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        buf.skip(12)

        assertEquals("fmt ", buf.readString(4))
        assertEquals(16, buf.readIntLe())
        assertEquals(1, buf.readShortLe().toInt())
        assertEquals(expectedNumChannels, buf.readShortLe().toInt())
        assertEquals(expectedSampleRate, buf.readIntLe())
        assertEquals(expectedByteRate, buf.readIntLe())
        assertEquals(expectedBlockAlign, buf.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, buf.readShortLe().toInt())

        assertEquals("data", buf.readString(4))
        assertEquals(expectedSubChunk2Size, buf.readIntLe())

        val payload = buf.readByteArray()
        assertTrue(payload.contentEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte())))
    }

    @Test
    fun `write with planar PCM-int throws UnsupportedFormatError`() {
        val testPath = Path(tempDir.toString(), "test_unsupported.wav")
        val planarFormat = AudioFormat(
            sampleRate = 44100,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.Sixteen,
                endianness = Endianness.Little,
                layout = SampleLayout.Planar,
                signed = true,
                packed = true,
            ),
        )

        val src = AudioSource.of(planarFormat, Buffer())
        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        assertFailsWith<AudioFileWriteError.UnsupportedFormat> {
            writer.write(src)
        }
    }

    @Test
    fun `write with 32-bit float mono writes IEEE-float WAV header`() = runTest {
        val testPath = Path(tempDir.toString(), "test_float32_mono.wav")
        val floatFmt = AudioFormat(
            sampleRate = 48000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmFloat(
                precision = FloatPrecision.F32,
                layout = SampleLayout.Interleaved,
            ),
        )

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)
        writer.write(AudioSource.of(floatFmt, 0x00, 0x00, 0x00, 0x00))

        val buf = SystemFileSystem.source(testPath).buffered()

        val expectedSubChunk2Size = 4
        val expectedChunkSize = 36 + expectedSubChunk2Size
        val expectedNumChannels = 1
        val expectedBitsPerSample = 32
        val expectedSampleRate = 48000
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        assertEquals("RIFF", buf.readString(4))
        assertEquals(expectedChunkSize, buf.readIntLe())
        assertEquals("WAVE", buf.readString(4))

        assertEquals("fmt ", buf.readString(4))
        assertEquals(16, buf.readIntLe())
        assertEquals(3, buf.readShortLe().toInt())
        assertEquals(expectedNumChannels, buf.readShortLe().toInt())
        assertEquals(expectedSampleRate, buf.readIntLe())
        assertEquals(expectedByteRate, buf.readIntLe())
        assertEquals(expectedBlockAlign, buf.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, buf.readShortLe().toInt())

        assertEquals("data", buf.readString(4))
        assertEquals(expectedSubChunk2Size, buf.readIntLe())

        val payload = buf.readByteArray()
        assertTrue(payload.contentEquals(byteArrayOf(0, 0, 0, 0)))
    }
}
