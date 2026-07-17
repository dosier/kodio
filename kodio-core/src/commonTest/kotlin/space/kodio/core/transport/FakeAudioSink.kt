package space.kodio.core.transport

import kotlin.time.Duration

/**
 * Test double for [AudioSink]: records every write with its seek epoch and
 * media byte offset, optionally advancing a [VirtualClock] per write.
 */
internal class FakeAudioSink(
    val clock: VirtualClock = VirtualClock(),
) : AudioSink {

    data class WriteEvent(
        val seekEpoch: Long,
        val mediaByteOffset: Long,
        val bytes: ByteArray,
        val clockNanos: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WriteEvent) return false
            return seekEpoch == other.seekEpoch &&
                mediaByteOffset == other.mediaByteOffset &&
                clockNanos == other.clockNanos &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = seekEpoch.hashCode()
            result = 31 * result + mediaByteOffset.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + clockNanos.hashCode()
            return result
        }
    }

    data class FlushEvent(
        val seekEpoch: Long,
        val clockNanos: Long,
    )

    private val writeEvents = mutableListOf<WriteEvent>()
    private val flushEvents = mutableListOf<FlushEvent>()

    /** Delay injected before each [write] (advances [clock], no wall sleep). */
    var writeDelay: Duration = Duration.ZERO

    val writes: List<WriteEvent>
        get() = writeEvents.toList()

    val flushes: List<FlushEvent>
        get() = flushEvents.toList()

    /** Writes whose epoch equals [epoch] (in order). */
    fun writesAtEpoch(epoch: Long): List<WriteEvent> =
        writeEvents.filter { it.seekEpoch == epoch }

    fun clear() {
        writeEvents.clear()
        flushEvents.clear()
        clock.reset()
    }

    override fun write(
        data: ByteArray,
        offset: Int,
        length: Int,
        seekEpoch: Long,
        mediaByteOffset: Long,
    ) {
        if (writeDelay > Duration.ZERO) clock.advance(writeDelay)
        writeEvents += WriteEvent(
            seekEpoch = seekEpoch,
            mediaByteOffset = mediaByteOffset,
            bytes = data.copyOfRange(offset, offset + length),
            clockNanos = clock.nanos,
        )
    }

    override fun flush(seekEpoch: Long) {
        flushEvents += FlushEvent(seekEpoch = seekEpoch, clockNanos = clock.nanos)
    }
}
