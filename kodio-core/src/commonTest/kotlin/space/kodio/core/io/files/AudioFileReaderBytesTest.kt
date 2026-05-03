package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.*
import space.kodio.core.*
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.aiff.writeAiff
import space.kodio.core.io.files.au.writeAu
import space.kodio.core.io.files.wav.writeWav

class AudioFileReaderBytesTest {

    private val pcm16Format = AudioFormat(
        sampleRate = 44100,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
            packed = true,
        )
    )

    private val pcmBytes = ByteArray(64) { (it * 7 - 13).toByte() }

    private fun makeWavBytes(): ByteArray {
        val sink = Buffer()
        writeWav(AudioSource.of(pcm16Format, Buffer().apply { write(pcmBytes) }), sink)
        return sink.readByteArray()
    }

    private fun makeAiffBytes(): ByteArray {
        val sink = Buffer()
        writeAiff(AudioSource.of(pcm16Format, Buffer().apply { write(pcmBytes) }), sink)
        return sink.readByteArray()
    }

    private fun makeAuBytes(): ByteArray {
        val sink = Buffer()
        writeAu(AudioSource.of(pcm16Format, Buffer().apply { write(pcmBytes) }), sink)
        return sink.readByteArray()
    }

    @Test
    fun `reads WAV from bytes via wav extension`() {
        val recording = AudioFileReader.read(makeWavBytes(), "clip.wav")
        assertEquals(pcm16Format, recording.format)
        assertContentEquals(pcmBytes, recording.toByteArray())
    }

    @Test
    fun `reads AIFF from bytes via aiff extension`() {
        val recording = AudioFileReader.read(makeAiffBytes(), "clip.aiff")
        assertEquals(pcm16Format.sampleRate, recording.format.sampleRate)
        assertEquals(pcm16Format.channels, recording.format.channels)
        assertContentEquals(pcmBytes, recording.toByteArray())
    }

    @Test
    fun `reads AU from bytes via au extension`() {
        val recording = AudioFileReader.read(makeAuBytes(), "clip.au")
        assertEquals(pcm16Format.sampleRate, recording.format.sampleRate)
        assertEquals(pcm16Format.channels, recording.format.channels)
        assertContentEquals(pcmBytes, recording.toByteArray())
    }

    @Test
    fun `extension matching is case insensitive`() {
        val recording = AudioFileReader.read(makeWavBytes(), "CLIP.WAV")
        assertEquals(pcm16Format, recording.format)
    }

    @Test
    fun `unsupported extension throws UnsupportedFormat`() {
        val ex = assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioFileReader.read(makeWavBytes(), "clip.m4a")
        }
        assertTrue(ex.message!!.contains("m4a", ignoreCase = true), "Error should mention extension: ${ex.message}")
    }

    @Test
    fun `extensionless filename throws UnsupportedFormat`() {
        assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioFileReader.read(makeWavBytes(), "no_extension")
        }
    }

    @Test
    fun `corrupt WAV bytes throw InvalidFile`() {
        // Header "XXXX" instead of "RIFF" should fail format detection inside readWav.
        val garbage = ByteArray(64) { 0x58 }  // 'X'
        val ex = assertFailsWith<AudioFileReadError> {
            AudioFileReader.read(garbage, "clip.wav")
        }
        assertNotNull(ex.message)
    }
}
