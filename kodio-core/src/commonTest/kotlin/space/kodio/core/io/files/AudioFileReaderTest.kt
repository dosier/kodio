package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import space.kodio.core.*
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.wav.readWav
import space.kodio.core.io.files.wav.writeWav
import kotlin.test.*

/**
 * Tests for WAV reading: full encode/decode round-trips, input method coverage,
 * error handling, and real-world WAV quirk tolerance.
 *
 * Note: File system tests are not supported on wasmJs/browser.
 */
@Ignore // File system operations not supported on wasmJs - see #15
class AudioFileReaderTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Path(SystemTemporaryDirectory, "kodio-reader-test")
        SystemFileSystem.createDirectories(tempDir)
    }

    /* ==================== Helpers ==================== */

    private fun fmtInt(
        rate: Int,
        channels: Channels,
        depth: IntBitDepth,
        signed: Boolean = true,
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmInt(
            bitDepth = depth,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = signed,
            packed = true
        )
    )

    private fun fmtFloat(
        rate: Int,
        channels: Channels,
        precision: FloatPrecision,
    ) = AudioFormat(
        sampleRate = rate,
        channels = channels,
        encoding = SampleEncoding.PcmFloat(
            precision = precision,
            layout = SampleLayout.Interleaved
        )
    )

    /** Generates a non-trivial ramp pattern so corruption is detectable. */
    private fun rampBytes(size: Int): ByteArray =
        ByteArray(size) { i -> (i % 251).toByte() }

    /** Writes WAV to a Buffer and returns the complete WAV bytes. */
    private fun encodeWav(format: AudioFormat, pcmData: ByteArray): ByteArray {
        val buf = Buffer()
        writeWav(AudioSource.of(format, *pcmData), buf)
        return buf.readByteArray()
    }

    /** Round-trip helper: encode -> decode -> assert format + payload match. */
    private fun assertRoundTrip(format: AudioFormat, pcmData: ByteArray) {
        val wavBytes = encodeWav(format, pcmData)
        val decoded = readWav(Buffer().apply { write(wavBytes) })

        assertEquals(format.sampleRate, decoded.format.sampleRate, "sampleRate mismatch")
        assertEquals(format.channels, decoded.format.channels, "channels mismatch")
        assertEquals(format.encoding, decoded.format.encoding, "encoding mismatch")

        val decodedPayload = decoded.source.readByteArray()
        assertTrue(
            pcmData.contentEquals(decodedPayload),
            "Payload mismatch: expected ${pcmData.size} bytes, got ${decodedPayload.size} bytes"
        )
    }

    /* ==================== Round-trip fidelity tests ==================== */

    @Test
    fun `round-trip 16-bit stereo 44100 Hz`() {
        val format = fmtInt(44100, Channels.Stereo, IntBitDepth.Sixteen)
        val pcm = rampBytes(44100 * 4) // 1 second: 44100 frames * 2 ch * 2 bytes
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip 16-bit mono 48000 Hz`() {
        val format = fmtInt(48000, Channels.Mono, IntBitDepth.Sixteen)
        val pcm = rampBytes(48000 * 2) // 1 second
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip 8-bit unsigned mono 22050 Hz`() {
        val format = fmtInt(22050, Channels.Mono, IntBitDepth.Eight, signed = false)
        val pcm = rampBytes(22050) // 1 second
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip 24-bit stereo 48000 Hz`() {
        val format = fmtInt(48000, Channels.Stereo, IntBitDepth.TwentyFour)
        val pcm = rampBytes(48000 * 2 * 3) // 1 second: 48000 frames * 2 ch * 3 bytes
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip 32-bit stereo 96000 Hz`() {
        val format = fmtInt(96000, Channels.Stereo, IntBitDepth.ThirtyTwo)
        val pcm = rampBytes(96000 * 2 * 4) // 1 second
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip float32 mono 48000 Hz`() {
        val format = fmtFloat(48000, Channels.Mono, FloatPrecision.F32)
        val pcm = rampBytes(48000 * 4) // 1 second
        assertRoundTrip(format, pcm)
    }

    @Test
    fun `round-trip float64 stereo 48000 Hz`() {
        val format = fmtFloat(48000, Channels.Stereo, FloatPrecision.F64)
        val pcm = rampBytes(48000 * 2 * 8) // 1 second
        assertRoundTrip(format, pcm)
    }

    /* ==================== Input method tests ==================== */

    @Test
    fun `fromBytes round-trip simulates Compose resource loading`() {
        val format = fmtInt(44100, Channels.Mono, IntBitDepth.Sixteen)
        val pcm = rampBytes(1024)
        val wavBytes = encodeWav(format, pcm)

        val recording = AudioRecording.fromBytes(wavBytes, AudioFileFormat.Wav)

        assertEquals(format.sampleRate, recording.format.sampleRate)
        assertEquals(format.channels, recording.format.channels)
        assertEquals(format.encoding, recording.format.encoding)
        assertTrue(pcm.contentEquals(recording.toByteArray()))
    }

    @Test
    fun `fromSource round-trip simulates stream-based loading`() {
        val format = fmtFloat(48000, Channels.Stereo, FloatPrecision.F32)
        val pcm = rampBytes(2048)
        val wavBytes = encodeWav(format, pcm)

        val source = Buffer().apply { write(wavBytes) }
        val recording = AudioRecording.fromSource(source, AudioFileFormat.Wav)

        assertEquals(format.sampleRate, recording.format.sampleRate)
        assertEquals(format.channels, recording.format.channels)
        assertEquals(format.encoding, recording.format.encoding)
        assertTrue(pcm.contentEquals(recording.toByteArray()))
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

    /* ==================== Error handling tests ==================== */

    @Test
    fun `non-RIFF input throws InvalidFile`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        assertFailsWith<AudioFileReadError.InvalidFile> {
            AudioRecording.fromBytes(garbage, AudioFileFormat.Wav)
        }
    }

    @Test
    fun `valid RIFF but not WAVE throws InvalidFile`() {
        val buf = Buffer().apply {
            writeString("RIFF")
            writeIntLe(4)
            writeString("AVI ")
        }
        assertFailsWith<AudioFileReadError.InvalidFile> {
            AudioRecording.fromSource(buf, AudioFileFormat.Wav)
        }
    }

    @Test
    fun `unsupported format code throws UnsupportedFormat`() {
        val buf = Buffer().apply {
            writeString("RIFF")
            writeIntLe(36 + 4) // riffSize
            writeString("WAVE")
            // fmt chunk with ADPCM (format code 2)
            writeString("fmt ")
            writeIntLe(16)
            writeShortLe(2) // ADPCM
            writeShortLe(1) // mono
            writeIntLe(44100)
            writeIntLe(44100) // byteRate
            writeShortLe(1) // blockAlign
            writeShortLe(4) // bitsPerSample
            // data chunk
            writeString("data")
            writeIntLe(4)
            write(byteArrayOf(0, 0, 0, 0))
        }
        assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioRecording.fromSource(buf, AudioFileFormat.Wav)
        }
    }

    @Test
    fun `truncated header throws InvalidFile`() {
        val truncated = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()
            // missing the rest
        )
        assertFailsWith<AudioFileReadError.InvalidFile> {
            AudioRecording.fromBytes(truncated, AudioFileFormat.Wav)
        }
    }

    @Test
    fun `too short to be RIFF throws InvalidFile`() {
        val tiny = byteArrayOf(0x01, 0x02)
        assertFailsWith<AudioFileReadError.InvalidFile> {
            AudioRecording.fromBytes(tiny, AudioFileFormat.Wav)
        }
    }

    @Test
    fun `unsupported file extension throws UnsupportedFormat`() {
        val testPath = Path(tempDir.toString(), "audio.flac")
        assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioFileReader(testPath, SystemFileSystem).read()
        }
    }

    /* ==================== Real-world WAV quirk tests ==================== */

    @Test
    fun `skips unknown chunks between fmt and data`() {
        val pcm = rampBytes(64)
        val format = fmtInt(44100, Channels.Mono, IntBitDepth.Sixteen)

        val buf = Buffer().apply {
            // RIFF header — calculate total size including extra chunks
            val listChunkTotalSize = 8 + 10 // "LIST" + size(4) + payload(10)
            val junkChunkTotalSize = 8 + 6  // "JUNK" + size(4) + payload(6)
            val fmtChunkTotalSize = 8 + 16  // "fmt " + size(4) + payload(16)
            val dataChunkTotalSize = 8 + pcm.size
            val riffPayload = 4 + fmtChunkTotalSize + listChunkTotalSize + junkChunkTotalSize + dataChunkTotalSize

            writeString("RIFF")
            writeIntLe(riffPayload)
            writeString("WAVE")

            // fmt chunk
            writeString("fmt ")
            writeIntLe(16)
            writeShortLe(1) // PCM
            writeShortLe(1) // mono
            writeIntLe(44100)
            writeIntLe(44100 * 2) // byteRate
            writeShortLe(2) // blockAlign
            writeShortLe(16) // bitsPerSample

            // LIST chunk (should be skipped)
            writeString("LIST")
            writeIntLe(10)
            write(ByteArray(10))

            // JUNK chunk (should be skipped)
            writeString("JUNK")
            writeIntLe(6)
            write(ByteArray(6))

            // data chunk
            writeString("data")
            writeIntLe(pcm.size)
            write(pcm)
        }

        val decoded = readWav(buf)
        assertEquals(format.sampleRate, decoded.format.sampleRate)
        assertEquals(format.channels, decoded.format.channels)
        assertEquals(format.encoding, decoded.format.encoding)

        val decodedPayload = decoded.source.readByteArray()
        assertTrue(pcm.contentEquals(decodedPayload))
    }

    @Test
    fun `handles extended fmt chunk with cbSize`() {
        val pcm = rampBytes(32)
        val format = fmtFloat(48000, Channels.Mono, FloatPrecision.F32)

        val fmtChunkSize = 18 // standard 16 + 2 bytes for cbSize
        val buf = Buffer().apply {
            val riffPayload = 4 + (8 + fmtChunkSize) + (8 + pcm.size)
            writeString("RIFF")
            writeIntLe(riffPayload)
            writeString("WAVE")

            // Extended fmt chunk
            writeString("fmt ")
            writeIntLe(fmtChunkSize)
            writeShortLe(3) // IEEE float
            writeShortLe(1) // mono
            writeIntLe(48000)
            writeIntLe(48000 * 4) // byteRate
            writeShortLe(4) // blockAlign
            writeShortLe(32) // bitsPerSample
            writeShortLe(0) // cbSize = 0 (no extra extension data)

            // data chunk
            writeString("data")
            writeIntLe(pcm.size)
            write(pcm)
        }

        val decoded = readWav(buf)
        assertEquals(format.sampleRate, decoded.format.sampleRate)
        assertEquals(format.channels, decoded.format.channels)
        assertEquals(format.encoding, decoded.format.encoding)

        val decodedPayload = decoded.source.readByteArray()
        assertTrue(pcm.contentEquals(decodedPayload))
    }

    @Test
    fun `handles fmt chunk with large extension data`() {
        val pcm = rampBytes(16)
        val extensionSize = 22 // WAVEFORMATEXTENSIBLE has 22 bytes of extension data
        val fmtChunkSize = 16 + 2 + extensionSize // standard + cbSize field + extension

        val buf = Buffer().apply {
            val riffPayload = 4 + (8 + fmtChunkSize) + (8 + pcm.size)
            writeString("RIFF")
            writeIntLe(riffPayload)
            writeString("WAVE")

            writeString("fmt ")
            writeIntLe(fmtChunkSize)
            writeShortLe(1) // PCM
            writeShortLe(1) // mono
            writeIntLe(44100)
            writeIntLe(44100 * 2)
            writeShortLe(2)
            writeShortLe(16)
            writeShortLe(extensionSize.toShort()) // cbSize
            write(ByteArray(extensionSize)) // extension data (skipped by reader)

            writeString("data")
            writeIntLe(pcm.size)
            write(pcm)
        }

        val decoded = readWav(buf)
        assertEquals(44100, decoded.format.sampleRate)
        assertEquals(Channels.Mono, decoded.format.channels)

        val decodedPayload = decoded.source.readByteArray()
        assertTrue(pcm.contentEquals(decodedPayload))
    }
}
