package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Documents JavaSound media-position math: line frame counter is not reset by
 * flush, so published position is [mediaBase] + (lineFrames - lineFramesAtSeekBase).
 */
class JvmTransportPositionTest {

    @Test
    fun mediaBasePlusFrameDelta_matchesExpectedPosition() {
        val sampleRate = 48_000
        val mediaBase = 250.milliseconds
        val lineFramesAtSeekBase = 10_000L
        val lineFramesNow = 10_000L + 4_800L // +100ms
        val framesSinceSeek = (lineFramesNow - lineFramesAtSeekBase).coerceAtLeast(0L)
        val position = mediaBase + (framesSinceSeek.toDouble() / sampleRate).seconds
        assertEquals(350.milliseconds, position)
    }
}
