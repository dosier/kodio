package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class AppleAudioFormatTest {

    @Test
    fun `Convert common AudioFormat to IOS AvAudioFormat`() {
        val iosAudioFormat = DefaultRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultRecordingAudioFormat.sampleRate.toDouble(), iosAudioFormat.sampleRate)
        assertEquals(DefaultRecordingAudioFormat.channels.count.toUInt(), iosAudioFormat.channelCount)
        assertEquals(DefaultRecordingAudioFormat.bitDepth.toAVAudioCommonFormat(), iosAudioFormat.commonFormat)
    }

    @Test
    fun `Convert IOS AvAudioFormat to common AudioFormat`() {
        val iosAudioFormat = DefaultRecordingAudioFormat.toIosAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultRecordingAudioFormat, iosAudioFormat.toCommonAudioFormat())
    }
}