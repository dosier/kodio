package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PcmSeekTest {

    private val format = AudioFormat(
        sampleRate = 48_000,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
        ),
    )

    @Test
    fun toSampleFrame_mapsDurationAtSampleRate() {
        assertEquals(0L, 0.milliseconds.toSampleFrame(48_000))
        assertEquals(24_000L, 500.milliseconds.toSampleFrame(48_000))
        assertEquals(48_000L, 1.seconds.toSampleFrame(48_000))
        assertEquals(0L, (-100).milliseconds.toSampleFrame(48_000))
    }

    @Test
    fun pcmSeekByteOffset_isFrameAligned() {
        val bytesPerFrame = format.bytesPerFrame // 2
        val frameCount = 48_000 // exactly 1 second at 48 kHz
        val byteCount = bytesPerFrame * frameCount

        assertEquals(0, pcmSeekByteOffset(0.milliseconds, format, byteCount))

        val mid = pcmSeekByteOffset(500.milliseconds, format, byteCount)
        assertEquals(byteCount / 2, mid)
        assertEquals(0, mid % bytesPerFrame)

        assertEquals(byteCount, pcmSeekByteOffset(1.seconds, format, byteCount))
    }

    @Test
    fun pcmSeekByteOffset_clampsToBuffer() {
        val byteCount = format.bytesPerFrame * 100
        assertEquals(0, pcmSeekByteOffset((-1).seconds, format, byteCount))
        assertEquals(byteCount, pcmSeekByteOffset(60.seconds, format, byteCount))
        assertEquals(0, pcmSeekByteOffset(1.seconds, format, 0))
    }

    @Test
    fun pcmSeekOffsetSeconds_clampsToDuration() {
        assertEquals(0.0, pcmSeekOffsetSeconds((-1).seconds, 10.0), absoluteTolerance = 1e-9)
        assertEquals(2.5, pcmSeekOffsetSeconds(2500.milliseconds, 10.0), absoluteTolerance = 1e-9)
        assertEquals(10.0, pcmSeekOffsetSeconds(20.seconds, 10.0), absoluteTolerance = 1e-9)
    }

    @Test
    fun pcmSeekByteOffset_openEndedHugePositionDoesNotWrapToZero() {
        // Frame index fits a Long but frame * bytesPerFrame does not.
        val offset = pcmSeekByteOffset((5e13).seconds, format, Long.MAX_VALUE)
        assertTrue(offset > 0L, "expected a far-future offset, got $offset")
        assertEquals(0L, offset % format.bytesPerFrame)
    }
}
