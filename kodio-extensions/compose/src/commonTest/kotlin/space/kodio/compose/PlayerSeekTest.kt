package space.kodio.compose

import space.kodio.compose.internal.pcmDuration
import space.kodio.compose.internal.seekByteOffset
import space.kodio.core.AudioQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlayerSeekTest {

    @Test
    fun seekByteOffset_mapsProgressToFrameAlignedBytes() {
        // Stereo 16-bit → 4 bytes/frame, 100 frames → 400 bytes
        val bytesPerFrame = 4
        val frameCount = 100L
        val byteCount = 400

        assertEquals(0, seekByteOffset(0f, frameCount, bytesPerFrame, byteCount))
        assertEquals(200, seekByteOffset(0.5f, frameCount, bytesPerFrame, byteCount))
        assertEquals(400, seekByteOffset(1f, frameCount, bytesPerFrame, byteCount))
        assertEquals(100, seekByteOffset(0.25f, frameCount, bytesPerFrame, byteCount))
    }

    @Test
    fun seekByteOffset_clampsAndAligns() {
        val bytesPerFrame = 4
        val frameCount = 10L
        val byteCount = 40

        assertEquals(0, seekByteOffset(-1f, frameCount, bytesPerFrame, byteCount))
        assertEquals(40, seekByteOffset(2f, frameCount, bytesPerFrame, byteCount))
        assertEquals(0, seekByteOffset(0.5f, 0L, bytesPerFrame, byteCount))
        assertEquals(0, seekByteOffset(0.5f, frameCount, 0, byteCount))
    }

    @Test
    fun seekByteOffset_handlesLongRecordingsWithoutIntOverflow() {
        // ~1 hour of stereo 16-bit @ 48kHz → 691_200_000 bytes (fits in Int)
        val bytesPerFrame = 4
        val frameCount = 48_000L * 3_600L
        val byteCount = (frameCount * bytesPerFrame).toInt()
        val mid = seekByteOffset(0.5f, frameCount, bytesPerFrame, byteCount)
        assertEquals(0, mid % bytesPerFrame)
        assertEquals(byteCount / 2, mid)
    }

    @Test
    fun pcmDuration_matchesFrameClock() {
        val format = AudioQuality.High.format // 48kHz
        assertEquals(1.seconds, pcmDuration(format, format.sampleRate.toLong()))
        assertEquals(500.milliseconds, pcmDuration(format, format.sampleRate.toLong() / 2))
        assertEquals(0.milliseconds, pcmDuration(format, 0L))
    }

    @Test
    fun pcmDuration_usesStandardFormat() {
        val format = AudioQuality.Standard.format
        val frames = format.sampleRate * 2L
        assertEquals(2.seconds, pcmDuration(format, frames))
    }
}
