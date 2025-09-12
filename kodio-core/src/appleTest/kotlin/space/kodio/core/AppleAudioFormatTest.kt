package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class AppleAudioFormatTest {

    @Test
    fun `Convert common AudioFormat to IOS AvAudioFormat`() {
        val iosAudioFormat = DefaultAppleRecordingAudioFormat.toAppleAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultAppleRecordingAudioFormat.sampleRate.toDouble(), iosAudioFormat.sampleRate)
        assertEquals(DefaultAppleRecordingAudioFormat.channels.count.toUInt(), iosAudioFormat.channelCount)
    }

    @Test
    fun `Convert IOS AvAudioFormat to common AudioFormat`() {
        val iosAudioFormat = DefaultAppleRecordingAudioFormat.toAppleAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultAppleRecordingAudioFormat, iosAudioFormat.toCommonAudioFormat())
    }
}