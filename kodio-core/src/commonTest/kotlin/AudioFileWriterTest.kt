import kotlinx.coroutines.test.runTest
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import space.kodio.core.AudioFormat
import space.kodio.core.BitDepth
import space.kodio.core.Channels
import space.kodio.core.Encoding
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileWriteError
import space.kodio.core.io.files.AudioFileWriter
import kotlin.test.*

/**
 * Contains tests for the AudioFileWriter class.
 * This test suite uses JUnit 5 and a temporary directory to perform real file I/O
 * in a controlled environment, ensuring the writer behaves as expected without
 * polluting the file system.
 */
class AudioFileWriterTest {

    lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Path(SystemTemporaryDirectory, "kodio-test")
        SystemFileSystem.createDirectories(tempDir)
    }

    /**
     * Tests the "happy path": writing a standard 16-bit, 44.1kHz, stereo PCM audio buffer.
     * It verifies that every single field in the WAV header is written correctly.
     */
    @Test
    fun `write with 16-bit stereo PCM format writes correct WAV header`() = runTest {
        // --- 1. Arrange ---
        val testPath = Path(tempDir.toString(), "test_16bit_stereo.wav")
        val format = AudioFormat(
            sampleRate = 44100,
            bitDepth = BitDepth.Sixteen,
            channels = Channels.Stereo,
            encoding = Encoding.Pcm.Signed
        )

        // Create a dummy audio payload (e.g., 4 bytes for one stereo 16-bit sample)

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // --- 2. Act ---
        writer.write(AudioSource.of(format, 0x12, 0x34, 0x56, 0x78))

        // --- 3. Assert ---
        // Read the written file back and verify its contents
        val writtenBuffer = SystemFileSystem.source(testPath).buffered()

        // Expected values
        val expectedSubChunk2Size = 4
        val expectedChunkSize = 36 + expectedSubChunk2Size
        val expectedNumChannels = 2
        val expectedBitsPerSample = 16
        val expectedSampleRate = 44100
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        // Verify RIFF Chunk
        assertEquals("RIFF", writtenBuffer.readString(4))
        assertEquals(expectedChunkSize, writtenBuffer.readIntLe())
        assertEquals("WAVE", writtenBuffer.readString(4))

        // Verify "fmt " sub-chunk
        assertEquals("fmt ", writtenBuffer.readString(4))
        assertEquals(16, writtenBuffer.readIntLe()) // Subchunk1Size for PCM
        assertEquals(1, writtenBuffer.readShortLe().toInt()) // AudioFormat code for PCM
        assertEquals(expectedNumChannels, writtenBuffer.readShortLe().toInt())
        assertEquals(expectedSampleRate, writtenBuffer.readIntLe())
        assertEquals(expectedByteRate, writtenBuffer.readIntLe())
        assertEquals(expectedBlockAlign, writtenBuffer.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, writtenBuffer.readShortLe().toInt())

        // Verify "data" sub-chunk
        assertEquals("data", writtenBuffer.readString(4))
        assertEquals(expectedSubChunk2Size, writtenBuffer.readIntLe())

        // Verify the audio payload was written correctly
        val payloadBytes = writtenBuffer.readByteArray()
        assertTrue(payloadBytes.contentEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
    }

    /**
     * Tests a different but still valid format: 8-bit, 22.05kHz, mono.
     * This ensures the calculations in the writer are dynamic and not hardcoded.
     */
    @Test
    fun `write with 8-bit mono PCM format writes correct WAV header`() {
        // --- 1. Arrange ---
        val testPath = Path(tempDir.toString(), "test_8bit_mono.wav")
        val format = AudioFormat(
            sampleRate = 22050,
            bitDepth = BitDepth.Eight,
            channels = Channels.Mono,
            encoding = Encoding.Pcm.Unsigned // 8-bit is often unsigned
        )

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // --- 2. Act ---
        writer.write( AudioSource.of(format, 0xAB.toByte(), 0xCD.toByte()))

        // --- 3. Assert ---
        val writtenBuffer = SystemFileSystem.source(testPath).buffered()

        // Expected values
        val expectedSubChunk2Size = 2
        val expectedNumChannels = 1
        val expectedBitsPerSample = 8
        val expectedSampleRate = 22050
        val expectedBlockAlign = expectedNumChannels * expectedBitsPerSample / 8
        val expectedByteRate = expectedSampleRate * expectedBlockAlign

        // Skip RIFF and WAVE, assume they are correct from the first test
        writtenBuffer.skip(12)

        // Verify "fmt " sub-chunk
        assertEquals("fmt ", writtenBuffer.readString(4))
        assertEquals(16, writtenBuffer.readIntLe())
        assertEquals(1, writtenBuffer.readShortLe().toInt())
        assertEquals(expectedNumChannels, writtenBuffer.readShortLe().toInt())
        assertEquals(expectedSampleRate, writtenBuffer.readIntLe())
        assertEquals(expectedByteRate, writtenBuffer.readIntLe())
        assertEquals(expectedBlockAlign, writtenBuffer.readShortLe().toInt())
        assertEquals(expectedBitsPerSample, writtenBuffer.readShortLe().toInt())

        // Verify "data" sub-chunk
        assertEquals("data", writtenBuffer.readString(4))
        assertEquals(expectedSubChunk2Size, writtenBuffer.readIntLe())

        // Verify payload
        val payloadBytes = writtenBuffer.readByteArray()
        assertTrue(payloadBytes.contentEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte())))
    }

    /**
     * Tests the error handling path: attempting to write a WAV file with an encoding
     * that is not supported (e.g., Encoding.Unknown).
     * It verifies that the correct exception type is thrown.
     */
    @Test
    fun `write with unsupported encoding throws UnsupportedFormatError`() {
        // --- 1. Arrange ---
        val testPath = Path(tempDir.toString(), "test_unsupported.wav")
        val format = AudioFormat(
            sampleRate = 44100,
            bitDepth = BitDepth.Sixteen,
            channels = Channels.Mono,
            encoding = Encoding.Unknown // This is the unsupported part
        )
        val audioDataBuffer = AudioSource.of(format, Buffer())
        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)

        // --- 2. Act & 3. Assert ---
        assertFailsWith<AudioFileWriteError.UnsupportedFormat> {
            writer.write(audioDataBuffer)
        }
    }
}
