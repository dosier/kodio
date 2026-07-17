package space.kodio.core.transport

/**
 * Platform-dumb device sink fed by [TransportController].
 *
 * Implementations must tolerate [flush] between writes and must not assume
 * contiguous epochs: a write stamped with an older [seekEpoch] is stale and
 * should be ignored by higher-level transcript assertions / device glue.
 */
internal interface AudioSink {
    /**
     * Enqueue or write [length] bytes from [data] at [offset].
     * [seekEpoch] is the transport epoch observed when the chunk was prepared.
     * [mediaByteOffset] is the absolute PCM offset of the first byte (for tests
     * and diagnostics); platforms may ignore it.
     */
    fun write(
        data: ByteArray,
        offset: Int,
        length: Int,
        seekEpoch: Long,
        mediaByteOffset: Long,
    )

    /** Discard queued device audio after a seek (epoch already bumped). */
    fun flush(seekEpoch: Long)
}
