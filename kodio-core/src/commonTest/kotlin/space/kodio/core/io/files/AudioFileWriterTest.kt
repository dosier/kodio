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
import space.kodio.core.io.AudioSource
import kotlin.test.*

/**
 * Contains tests for the AudioFileWriter class with the new AudioFormat model.
 */
class AudioFileWriterTest {

    lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Path(SystemTemporaryDirectory, "kodio-test")
        SystemFileSystem.createDirectories(tempDir)
    }

    /* -------------------- Helpers -------------------- */

    private fun fmtInt(
        rate: Int,
        channels: Channels,
        depth: IntBitDepth,
        signed: Boolean = true,
        endianness: Endianness = Endianness.Little,
        layout: SampleLayout = SampleLayout.Interleaved
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmInt(
            bitDepth = depth,
            endianness = endianness,
            layout = layout,
            signed = signed,
            packed = true
        )
    )

    /* -------------------- Tests -------------------- */

    /**
     * Happy path: 16-bit, 44.1kHz, stereo PCM-int (interleaved, LE).
     */
    @Test
    fun `write with 16-bit stereo PCM format writes correct WAV header`() = runTest {
        // Arrange
        val testPath = Path(tempDir.toString(), "test_16bit_stereo.wav")
        val format = fmtInt(
            rate = 44100,
            channels = Channels.Stereo,
            depth = IntBitDepth.Sixteen,
            signed = true,
            endianness = Endianness.Little
        )
        // 4 bytes of payload (one stereo frame: 2 bytes L + 2 bytes R)
        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // Act
        writer.write(AudioSource.of(format, 0x12, 0x34, 0x56, 0x78))

        // Assert
        val buf = SystemFileSystem.source(testPath).buffered()

        val expectedSubChunk2Size = 4
        val expectedChunkSize = 36 + expectedSubChunk2Size
        val expectedNumChannels = 2
        val expectedBitsPerSample = 16
        val expectedSampleRate = 44100
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        // RIFF
        assertEquals("RIFF", buf.readString(4))
        assertEquals(expectedChunkSize, buf.readIntLe())
        assertEquals("WAVE", buf.readString(4))

        // fmt
        assertEquals("fmt ", buf.readString(4))
        assertEquals(16, buf.readIntLe())                 // PCM/IEEE-float basic fmt chunk
        assertEquals(1, buf.readShortLe().toInt())        // AudioFormat: 1 = PCM-int
        assertEquals(expectedNumChannels, buf.readShortLe().toInt())
        assertEquals(expectedSampleRate, buf.readIntLe())
        assertEquals(expectedByteRate, buf.readIntLe())
        assertEquals(expectedBlockAlign, buf.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, buf.readShortLe().toInt())

        // data
        assertEquals("data", buf.readString(4))
        assertEquals(expectedSubChunk2Size, buf.readIntLe())

        // payload
        val payload = buf.readByteArray()
        assertTrue(payload.contentEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
    }

    /**
     * 8-bit, 22.05kHz, mono unsigned PCM-int (WAV PCM-int code=1).
     */
    @Test
    fun `write with 8-bit mono PCM format writes correct WAV header`() {
        // Arrange
        val testPath = Path(tempDir.toString(), "test_8bit_mono.wav")
        val format = fmtInt(
            rate = 22050,
            channels = Channels.Mono,
            depth = IntBitDepth.Eight,
            signed = false, // 8-bit often unsigned in WAV
            endianness = Endianness.Little
        )

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // Act
        writer.write(AudioSource.of(format, 0xAB.toByte(), 0xCD.toByte()))

        // Assert
        val buf = SystemFileSystem.source(testPath).buffered()

        val expectedSubChunk2Size = 2
        val expectedNumChannels = 1
        val expectedBitsPerSample = 8
        val expectedSampleRate = 22050
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        // Skip "RIFF....WAVE"
        buf.skip(12)

        assertEquals("fmt ", buf.readString(4))
        assertEquals(16, buf.readIntLe())
        assertEquals(1, buf.readShortLe().toInt()) // PCM-int
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

    /**
     * Error path: WAV writer rejects planar data (WAV requires interleaved).
     */
    @Test
    fun `write with planar PCM-int throws UnsupportedFormatError`() {
        // Arrange
        val testPath = Path(tempDir.toString(), "test_unsupported.wav")
        val planarFormat = AudioFormat(
            sampleRate = 44100,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.Sixteen,
                endianness = Endianness.Little,
                layout = SampleLayout.Planar, // <-- not supported by WAV writer
                signed = true,
                packed = true
            )
        )

        val src = AudioSource.of(planarFormat, Buffer())
        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // Act & Assert
        assertFailsWith<AudioFileWriteError.UnsupportedFormat> {
            writer.write(src)
        }
    }

    /**
     * Optional: verify IEEE float path header (format code 3) with 32-bit float mono.
     */
    @Test
    fun `write with 32-bit float mono writes IEEE-float WAV header`() = runTest {
        // Arrange
        val testPath = Path(tempDir.toString(), "test_float32_mono.wav")
        val floatFmt = AudioFormat(
            sampleRate = 48000,
            channels = Channels.Mono,
            encoding = SampleEncoding.PcmFloat(
                precision = FloatPrecision.F32,
                layout = SampleLayout.Interleaved
            )
        )

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)
        // Four bytes: one Float32 value (raw bits). We'll just write 0.0f bytes.
        writer.write(AudioSource.of(floatFmt, 0x00, 0x00, 0x00, 0x00))

        // Assert
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
        assertEquals(3, buf.readShortLe().toInt()) // 3 = IEEE float
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