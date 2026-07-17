package space.kodio.core.transport

import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.IntBitDepth
import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PlaybackBufferTest {

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
    fun retention_unbounded_isRing() {
        val mode = resolvePlaybackBufferMode(null, format, PlaybackBufferConfig(maxBytes = 1024, maxDuration = 1.seconds))
        assertIs<PlaybackBufferMode.Ring>(mode)
    }

    @Test
    fun retention_finiteUnderCap_isGrowable() {
        val mode = resolvePlaybackBufferMode(512, format, PlaybackBufferConfig(maxBytes = 1024, maxDuration = 60.seconds))
        assertIs<PlaybackBufferMode.Growable>(mode)
        assertEquals(512, mode.reserveBytes)
    }

    @Test
    fun retention_finiteOverCap_isRing() {
        val mode = resolvePlaybackBufferMode(
            knownByteCount = 10_000,
            format = format,
            config = PlaybackBufferConfig(maxBytes = 1024, maxDuration = 60.seconds),
        )
        assertIs<PlaybackBufferMode.Ring>(mode)
    }

    @Test
    fun ring_appendPastCapacity_evictsOldest() {
        val capacity = 8L
        val buffer = RingPlaybackBuffer(format, capacity)
        buffer.append(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(0L until 8L, buffer.bufferedRange)

        buffer.append(byteArrayOf(9, 10))
        assertEquals(2L until 10L, buffer.bufferedRange)
        assertEquals(10L, buffer.writePosition)

        val dst = ByteArray(4)
        assertEquals(0, buffer.read(0, dst, 0, 4), "read below range returns 0")
        // Absolute 2..4 still holds the original bytes at those offsets; 9/10 landed at 8..10.
        assertEquals(2, buffer.read(2, dst, 0, 2))
        assertEquals(3.toByte(), dst[0])
        assertEquals(4.toByte(), dst[1])
        assertEquals(2, buffer.read(8, dst, 0, 2))
        assertEquals(9.toByte(), dst[0])
        assertEquals(10.toByte(), dst[1])
        assertFalse(buffer.contains(0))
        assertTrue(buffer.contains(2))
    }

    @Test
    fun ring_acquireWrite_handlesWrapWithoutAllocatingExtraSlab() {
        val buffer = RingPlaybackBuffer(format, capacityBytes = 8)
        buffer.append(ByteArray(6) { it.toByte() })
        val acquire = BufferAcquire()
        val first = buffer.acquireWrite(4, acquire)
        assertEquals(2, first, "only contiguous tail until wrap")
        assertEquals(6, acquire.offset)
        ByteArray(2) { 100 }.copyInto(acquire.slab, acquire.offset)
        buffer.commitWrite(2)

        val second = buffer.acquireWrite(4, acquire)
        assertEquals(4, second)
        assertEquals(0, acquire.offset)
        ByteArray(4) { 101 }.copyInto(acquire.slab, acquire.offset, 0, 4)
        buffer.commitWrite(4)

        assertEquals(12L, buffer.writePosition)
        assertEquals(4L until 12L, buffer.bufferedRange)
    }

    @Test
    fun growable_growsPastUnderEstimatedReserve() {
        val buffer = GrowablePlaybackBuffer(format, reserveBytes = 4)
        assertIs<PlaybackBufferMode.Growable>(buffer.mode)
        val chunk = ByteArray(20) { it.toByte() }
        buffer.append(chunk)
        assertEquals(20L, buffer.writePosition)
        assertEquals(0L until 20L, buffer.bufferedRange)

        val dst = ByteArray(20)
        assertEquals(20, buffer.read(0, dst, 0, 20))
        assertTrue(dst.contentEquals(chunk))
    }

    @Test
    fun factory_selectsBackendsPerPolicy() {
        val ring = PlaybackBuffer(format, knownByteCount = null, PlaybackBufferConfig(maxBytes = 64, maxDuration = 1.seconds))
        assertIs<PlaybackBufferMode.Ring>(ring.mode)

        val growable = PlaybackBuffer(format, knownByteCount = 32, PlaybackBufferConfig(maxBytes = 64, maxDuration = 60.seconds))
        assertIs<PlaybackBufferMode.Growable>(growable.mode)

        val oversized = PlaybackBuffer(format, knownByteCount = 10_000, PlaybackBufferConfig(maxBytes = 64, maxDuration = 1.seconds))
        assertIs<PlaybackBufferMode.Ring>(oversized.mode)
    }

    @Test
    fun append_copiesCallerArray() {
        val buffer = GrowablePlaybackBuffer(format, reserveBytes = 8)
        val chunk = byteArrayOf(1, 2, 3, 4)
        buffer.append(chunk)
        chunk[0] = 99
        val dst = ByteArray(4)
        buffer.read(0, dst, 0, 4)
        assertEquals(1.toByte(), dst[0], "buffer must not alias caller array")
    }
}
