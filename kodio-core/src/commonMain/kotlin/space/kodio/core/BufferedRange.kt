package space.kodio.core

import kotlin.time.Duration

/**
 * Inclusive-exclusive media time still held in the transport buffer.
 *
 * Empty when [endExclusive] ≤ [start] (nothing buffered / seekable).
 */
data class BufferedRange(
    val start: Duration,
    val endExclusive: Duration,
) {
    operator fun contains(position: Duration): Boolean =
        position >= start && position < endExclusive

    val isEmpty: Boolean
        get() = endExclusive <= start

    companion object {
        val Empty: BufferedRange = BufferedRange(Duration.ZERO, Duration.ZERO)
    }
}
