import space.kodio.core.DefaultIosRecordingAudioFormat
import space.kodio.core.toAVAudioCommonFormat
import space.kodio.core.toCommonAudioFormat
import space.kodio.core.toIosAudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class IosAudioFormatTest {

    @Test
    fun `Convert common AudioFormat to IOS AvAudioFormat`() {
        val iosAudioFormat = DefaultIosRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultIosRecordingAudioFormat.sampleRate.toDouble(), iosAudioFormat.sampleRate)
        assertEquals(DefaultIosRecordingAudioFormat.channels.count.toUInt(), iosAudioFormat.channelCount)
        assertEquals(DefaultIosRecordingAudioFormat.bitDepth.toAVAudioCommonFormat(), iosAudioFormat.commonFormat)
    }

    @Test
    fun `Convert IOS AvAudioFormat to common AudioFormat`() {
        val iosAudioFormat = DefaultIosRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultIosRecordingAudioFormat, iosAudioFormat.toCommonAudioFormat())
    }
}