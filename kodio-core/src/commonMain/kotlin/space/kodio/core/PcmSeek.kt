package space.kodio.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Converts a playback [position] to a sample-frame index at [sampleRate].
 *
 * Negative positions clamp to `0`. The result is not clamped to a buffer length;
 * callers should coerce against their total frame count.
 */
fun Duration.toSampleFrame(sampleRate: Int): Long {
    if (sampleRate <= 0 || isNegative()) return 0L
    if (isInfinite()) return Long.MAX_VALUE
    val seconds = toDouble(DurationUnit.SECONDS)
    if (seconds <= 0.0) return 0L
    return (seconds * sampleRate.toDouble())
        .toLong()
        .coerceAtLeast(0L)
}

/**
 * Frame-aligned byte offset into a PCM buffer for [position].
 *
 * Returns `0` when the format or buffer cannot be indexed. The offset is always
 * a multiple of [AudioFormat.bytesPerFrame] and within `0..byteCount`.
 */
fun pcmSeekByteOffset(
    position: Duration,
    format: AudioFormat,
    byteCount: Int,
): Int {
    if (byteCount <= 0) return 0
    val offset = pcmSeekByteOffset(position, format, byteCount.toLong())
    return offset.coerceIn(0L, byteCount.toLong()).toInt()
}

/**
 * Frame-aligned absolute byte offset for [position] on a [Long]-sized timeline.
 *
 * When [byteCount] is [Long.MAX_VALUE] (unknown / open-ended ceiling), the result
 * is only clamped to be non-negative and frame-aligned, not to a buffer end.
 */
fun pcmSeekByteOffset(
    position: Duration,
    format: AudioFormat,
    byteCount: Long,
): Long {
    val bytesPerFrame = format.bytesPerFrame
    if (bytesPerFrame <= 0 || byteCount <= 0L || format.sampleRate <= 0) return 0L

    val bpf = bytesPerFrame.toLong()
    val totalFrames = if (byteCount == Long.MAX_VALUE) Long.MAX_VALUE else byteCount / bpf
    if (totalFrames <= 0L) return 0L

    val targetFrame = position.toSampleFrame(format.sampleRate)
        .coerceIn(0L, totalFrames)
    // Open-ended ceiling: targetFrame is only clamped to Long.MAX_VALUE, so guard the multiply.
    val offset = if (targetFrame > Long.MAX_VALUE / bpf) Long.MAX_VALUE else targetFrame * bpf
    val capped = if (byteCount == Long.MAX_VALUE) offset.coerceAtLeast(0L) else offset.coerceIn(0L, byteCount)
    return capped - (capped % bpf)
}

/**
 * Seconds offset into a PCM timeline for [position], clamped to
 * `[0, durationSeconds]` when [durationSeconds] is positive.
 */
fun pcmSeekOffsetSeconds(
    position: Duration,
    durationSeconds: Double,
): Double {
    val seconds = if (position.isNegative()) 0.0 else position.toDouble(DurationUnit.SECONDS)
    if (durationSeconds <= 0.0) return seconds.coerceAtLeast(0.0)
    return seconds.coerceIn(0.0, durationSeconds)
}

/**
 * Media duration corresponding to an absolute PCM [byteOffset] in [format].
 * Frame-aligned; partial trailing frames are ignored.
 */
fun AudioFormat.durationOfByteOffset(byteOffset: Long): Duration {
    val bpf = bytesPerFrame
    if (bpf <= 0 || sampleRate <= 0 || byteOffset <= 0L) return Duration.ZERO
    val frames = byteOffset / bpf
    // Avoid floating Duration construction for long recordings.
    val nanos = frames * 1_000_000_000L / sampleRate
    return nanos.nanoseconds
}
