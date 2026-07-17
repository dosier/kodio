package space.kodio.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Retention and priming knobs for the transport [space.kodio.core.transport.PlaybackBuffer].
 *
 * Out-of-range seek policy is not configurable in v1: targets outside the retained
 * window return [SeekResult.Unsupported] (known-finite ahead-of-ingest seeks are
 * accepted as a pending cursor; see transport seek semantics).
 */
data class PlaybackBufferConfig(
    /** Hard cap for growable storage; also the ring capacity upper bound. */
    val maxBytes: Long = 64L * 1024 * 1024,
    /** Alternative capacity cap: `min(maxBytes, duration × rate × frame)`. */
    val maxDuration: Duration = 60.seconds,
    /** How much PCM to prime into the device after a seek before restarting. */
    val devicePrimeDuration: Duration = 80.milliseconds,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxDuration.isPositive()) { "maxDuration must be positive" }
        require(!devicePrimeDuration.isNegative()) { "devicePrimeDuration must be non-negative" }
    }
}
