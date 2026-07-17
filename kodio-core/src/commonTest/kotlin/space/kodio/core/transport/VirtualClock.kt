package space.kodio.core.transport

import kotlin.time.Duration

/**
 * Deterministic clock for transport tests. Advances only when tests or
 * [FakeAudioSink] inject delay — never wall-clock sleep.
 */
internal class VirtualClock {
    var nanos: Long = 0L
        private set

    fun advance(duration: Duration) {
        require(!duration.isNegative()) { "cannot advance by negative duration" }
        if (duration == Duration.ZERO) return
        nanos += duration.inWholeNanoseconds
    }

    fun reset() {
        nanos = 0L
    }
}
