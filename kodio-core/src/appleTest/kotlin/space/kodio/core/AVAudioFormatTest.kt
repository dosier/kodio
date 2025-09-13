package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class AVAudioFormatTest {

    @Test
    fun `Convert common AudioFormat to AVAudioFormat`() {
        val iosAudioFormat = DefaultAppleRecordingAudioFormat.toAVAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultAppleRecordingAudioFormat.sampleRate.toDouble(), iosAudioFormat.sampleRate)
        assertEquals(DefaultAppleRecordingAudioFormat.channels.count.toUInt(), iosAudioFormat.channelCount)
    }

    @Test
    fun `Convert AVAudioFormat to common AudioFormat`() {
        val iosAudioFormat = DefaultAppleRecordingAudioFormat.toAVAudioFormat()
        assertNotNull(iosAudioFormat)
        assertEquals(DefaultAppleRecordingAudioFormat, iosAudioFormat.toCommonAudioFormat())
    }
}