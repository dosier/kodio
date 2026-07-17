package space.kodio.core.transport

import space.kodio.core.AudioFormat
import space.kodio.core.PlaybackBufferConfig
import space.kodio.core.toSampleFrame
import kotlin.time.Duration

/**
 * Storage backend selected by the retention policy from the converted flow's
 * [space.kodio.core.AudioFlow.knownByteCount].
 */
internal sealed class PlaybackBufferMode {
    /** Sliding window; append always accepts and may evict the oldest prefix. */
    data class Ring(val capacityBytes: Long) : PlaybackBufferMode()

    /** Grows toward EOS; [reserveBytes] is a hint (may grow past an under-estimate). */
    data class Growable(val reserveBytes: Long) : PlaybackBufferMode()
}

/**
 * Normative retention decision (plan Phase 0):
 *
 * - unbounded (`knownByteCount == null`) → ring
 * - finite and `knownByteCount <= maxBytes` → growable
 * - finite over cap → ring (limited seek-back; no full-file materialize)
 */
internal fun resolvePlaybackBufferMode(
    knownByteCount: Long?,
    format: AudioFormat,
    config: PlaybackBufferConfig,
): PlaybackBufferMode {
    val capacity = ringCapacityBytes(format, config)
    return when {
        knownByteCount == null -> PlaybackBufferMode.Ring(capacity)
        knownByteCount <= config.maxBytes ->
            PlaybackBufferMode.Growable(reserveBytes = knownByteCount.coerceAtLeast(0L))
        else -> PlaybackBufferMode.Ring(capacity)
    }
}

internal fun ringCapacityBytes(format: AudioFormat, config: PlaybackBufferConfig): Long {
    val bpf = format.bytesPerFrame.toLong().coerceAtLeast(1L)
    val fromDuration = format.bytesForDuration(config.maxDuration)
    val raw = minOf(config.maxBytes, fromDuration).coerceAtLeast(bpf)
    // Keep capacity frame-aligned so wrap boundaries stay on frame edges.
    return (raw / bpf) * bpf
}

internal fun AudioFormat.bytesForDuration(duration: Duration): Long {
    val bpf = bytesPerFrame.toLong()
    if (bpf <= 0L || sampleRate <= 0 || duration.isNegative() || duration == Duration.ZERO) {
        return 0L
    }
    if (duration.isInfinite()) return Long.MAX_VALUE
    val frames = duration.toSampleFrame(sampleRate)
    if (frames == Long.MAX_VALUE) return Long.MAX_VALUE
    val product = frames * bpf
    return if (product / bpf != frames) Long.MAX_VALUE else product
}
