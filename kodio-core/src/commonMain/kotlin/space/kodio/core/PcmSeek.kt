package space.kodio.core

import kotlin.time.Duration
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
    val bytesPerFrame = format.bytesPerFrame
    if (bytesPerFrame <= 0 || byteCount <= 0 || format.sampleRate <= 0) return 0

    val totalFrames = (byteCount / bytesPerFrame).toLong()
    if (totalFrames <= 0L) return 0

    val targetFrame = position.toSampleFrame(format.sampleRate)
        .coerceIn(0L, totalFrames)
    val offset = (targetFrame * bytesPerFrame.toLong())
        .coerceIn(0L, byteCount.toLong())
    val aligned = offset - (offset % bytesPerFrame.toLong())
    return aligned.toInt().coerceIn(0, byteCount)
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
