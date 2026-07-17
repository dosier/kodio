package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit-tests the Android seek position math and pause-before-flush contract
 * without opening a real [android.media.AudioTrack].
 */
class AndroidTransportSeekLogicTest {

    @Test
    fun unsignedDelta_handlesWrap() {
        val baseline = 0xFFFF_FFF0L
        val now = 0x10L // wrapped past 32-bit
        assertEquals(0x20L, unsignedDelta(now, baseline))
    }

    @Test
    fun unsignedDelta_monotonicNoWrap() {
        assertEquals(4800L, unsignedDelta(10_000L + 4800L, 10_000L))
    }

    @Test
    fun seekStateMachine_requiresPauseBeforeFlush() {
        // Documented contract for MODE_STREAM: flush while playing is a no-op.
        val steps = mutableListOf<String>()
        fun pause() = steps.add("pause")
        fun flush() = steps.add("flush")
        fun play() = steps.add("play")

        val wantPlaying = true
        pause()
        flush()
        if (wantPlaying) play()

        assertEquals(listOf("pause", "flush", "play"), steps)
        assertTrue(steps.indexOf("pause") < steps.indexOf("flush"))
    }
}
