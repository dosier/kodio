import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class IosAudioFormatTest {

    @Test
    fun `Convert common AudioFormat to IOS AvAudioFormat`() {
        val iosAudioFormat = DefaultIosRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultIosRecordingAudioFormat.sampleRate.toDouble(), iosAudioFormat.sampleRate)
        assertEquals(DefaultIosRecordingAudioFormat.channels.toUInt(), iosAudioFormat.channelCount)
        assertEquals(bitDepthToAVAudioCommonFormat(DefaultIosRecordingAudioFormat.bitDepth), iosAudioFormat.commonFormat)
    }

    @Test
    fun `Convert IOS AvAudioFormat to common AudioFormat`() {
        val iosAudioFormat = DefaultIosRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultIosRecordingAudioFormat, iosAudioFormat.toCommonAudioFormat())
    }
}