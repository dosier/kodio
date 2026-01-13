package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for [AudioQuality] presets.
 */
class AudioQualityTest {

    @Test
    fun `Voice quality has correct format`() {
        val quality = AudioQuality.Voice
        val format = quality.format
        
        assertEquals(16000, format.sampleRate)
        assertEquals(Channels.Mono, format.channels)
        assertEquals(2, format.bytesPerSample) // 16-bit
    }

    @Test
    fun `Standard quality has correct format`() {
        val quality = AudioQuality.Standard
        val format = quality.format
        
        assertEquals(44100, format.sampleRate)
        assertEquals(Channels.Mono, format.channels)
        assertEquals(2, format.bytesPerSample) // 16-bit
    }

    @Test
    fun `High quality has correct format`() {
        val quality = AudioQuality.High
        val format = quality.format
        
        assertEquals(48000, format.sampleRate)
        assertEquals(Channels.Stereo, format.channels)
        assertEquals(2, format.bytesPerSample) // 16-bit
    }

    @Test
    fun `Lossless quality has correct format`() {
        val quality = AudioQuality.Lossless
        val format = quality.format
        
        assertEquals(96000, format.sampleRate)
        assertEquals(Channels.Stereo, format.channels)
        assertEquals(3, format.bytesPerSample) // 24-bit
    }

    @Test
    fun `Default quality is Standard`() {
        assertEquals(AudioQuality.Standard, AudioQuality.Default)
    }

    @Test
    fun `All qualities have unique formats`() {
        val formats = AudioQuality.entries.map { it.format }
        val uniqueFormats = formats.toSet()
        assertEquals(formats.size, uniqueFormats.size, "All quality presets should have unique formats")
    }

    @Test
    fun `Quality formats have valid bytesPerFrame`() {
        AudioQuality.entries.forEach { quality ->
            val format = quality.format
            val expectedBytesPerFrame = format.bytesPerSample * format.channels.count
            assertEquals(
                expectedBytesPerFrame,
                format.bytesPerFrame,
                "bytesPerFrame should be bytesPerSample * channels for ${quality.name}"
            )
        }
    }
}


