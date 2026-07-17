package space.kodio.core.io.files

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioFormat
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.IntBitDepth
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.wav.writeWav
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioFormatDetectionTest {

    private fun tinyWav(): ByteArray {
        val format = AudioFormat(
            sampleRate = 8000,
            channels = Channels.Mono,
            encoding = space.kodio.core.SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.Sixteen,
                endianness = space.kodio.core.Endianness.Little,
                layout = space.kodio.core.SampleLayout.Interleaved,
                signed = true,
            ),
        )
        val pcm = ByteArray(64) { it.toByte() }
        val buf = Buffer()
        writeWav(AudioSource.of(format, *pcm), buf)
        return buf.readByteArray()
    }

    @Test
    fun detectReturnsWavForRiffWaveBytes() {
        assertEquals(AudioFileFormat.Wav, AudioFileFormat.detect(tinyWav()))
    }

    @Test
    fun detectReturnsNullForUnknownBytes() {
        assertNull(AudioFileFormat.detect(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun emptyBytesThrowInvalidFile() {
        assertFailsWith<AudioFileReadError.InvalidFile> {
            AudioRecording.fromBytes(ByteArray(0))
        }
    }

    @Test
    fun unknownBytesWithoutFileNameThrowUnsupportedFormat() {
        assertFailsWith<AudioFileReadError.UnsupportedFormat> {
            AudioRecording.fromBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        }
    }

    @Test
    fun magicWinsOverMismatchedExtension() {
        val wav = tinyWav()
        val recording = AudioRecording.fromBytes(wav, fileName = "clip.mp3")
        assertEquals(8000, recording.format.sampleRate)
        assertTrue(recording.toByteArray().isNotEmpty())
    }

    @Test
    fun extensionFallbackWhenMagicMissing() {
        // Truncated/non-magic prefix but known extension + explicit format overload.
        val wav = tinyWav()
        val recording = AudioRecording.fromBytes(wav, AudioFileFormat.Wav)
        assertEquals(AudioFileFormat.Wav.extension, "wav")
        assertEquals(8000, recording.format.sampleRate)
    }

    @Test
    fun fromExtensionRecognizesAliases() {
        assertEquals(AudioFileFormat.Wav, AudioFileFormat.fromExtension("wave"))
        assertEquals(AudioFileFormat.Aiff, AudioFileFormat.fromExtension("aif"))
        assertEquals(AudioFileFormat.Au, AudioFileFormat.fromExtension("snd"))
        assertEquals(AudioFileFormat.Mp3, AudioFileFormat.fromExtension("mp3"))
        assertNull(AudioFileFormat.fromExtension("flac"))
    }
}
