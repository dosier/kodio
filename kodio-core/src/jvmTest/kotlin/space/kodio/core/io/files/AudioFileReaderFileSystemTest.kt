package space.kodio.core.io.files

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import space.kodio.core.*
import space.kodio.core.fmtInt
import space.kodio.core.rampBytes
import space.kodio.core.io.AudioSource
import kotlin.test.*

class AudioFileReaderFileSystemTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Path(SystemTemporaryDirectory, "kodio-reader-test")
        SystemFileSystem.createDirectories(tempDir)
    }

    @Test
    fun `fromFile round-trip via filesystem`() {
        val format = fmtInt(44100, Channels.Stereo, IntBitDepth.Sixteen)
        val pcm = rampBytes(512)
        val testPath = Path(tempDir.toString(), "roundtrip_file.wav")

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)
        writer.write(AudioSource.of(format, *pcm))

        val recording = AudioRecording.fromFile(testPath, SystemFileSystem)

        assertEquals(format.sampleRate, recording.format.sampleRate)
        assertEquals(format.channels, recording.format.channels)
        assertEquals(format.encoding, recording.format.encoding)
        assertTrue(pcm.contentEquals(recording.toByteArray()))
    }

    @Test
    fun `AudioFileReader read round-trip via filesystem`() {
        val format = fmtInt(22050, Channels.Mono, IntBitDepth.Eight, signed = false)
        val pcm = rampBytes(256)
        val testPath = Path(tempDir.toString(), "reader_roundtrip.wav")

        val writer = AudioFileWriter(AudioFileFormat.Wav, testPath, SystemFileSystem)
        writer.write(AudioSource.of(format, *pcm))

        val reader = AudioFileReader(testPath, SystemFileSystem)
        val recording = reader.read()

        assertEquals(format.sampleRate, recording.format.sampleRate)
        assertEquals(format.channels, recording.format.channels)
        assertEquals(format.encoding, recording.format.encoding)
        assertTrue(pcm.contentEquals(recording.toByteArray()))
    }

    @Test
    fun `unsupported file extension throws UnsupportedFormat`() {
        val testPath = Path(tempDir.toString(), "audio.flac")
        assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioFileReader(testPath, SystemFileSystem).read()
        }
    }
}
