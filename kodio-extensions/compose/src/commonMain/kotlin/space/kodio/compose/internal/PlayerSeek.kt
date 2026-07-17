package space.kodio.compose.internal

import space.kodio.core.AudioFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Maps normalized playback [progress] (`0f..1f`) to a frame-aligned byte offset
 * into PCM data of [byteCount] bytes.
 *
 * Uses double-precision frame indexing so long recordings stay accurate, and
 * clamps to the valid byte range.
 */
internal fun seekByteOffset(
    progress: Float,
    frameCount: Long,
    bytesPerFrame: Int,
    byteCount: Int,
): Int {
    if (frameCount <= 0L || bytesPerFrame <= 0 || byteCount <= 0) return 0
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return 0
    if (clamped >= 1f) return byteCount

    val targetFrame = (clamped.toDouble() * frameCount)
        .toLong()
        .coerceIn(0L, frameCount)
    val offset = (targetFrame * bytesPerFrame.toLong())
        .coerceIn(0L, byteCount.toLong())
    // Keep frame alignment even if coercion clipped mid-frame.
    val aligned = offset - (offset % bytesPerFrame.toLong())
    return aligned.toInt().coerceIn(0, byteCount)
}

/**
 * Duration of [frameCount] PCM frames at [format]'s sample rate.
 *
 * Prefer this over container metadata when seeking so progress and byte offsets
 * share the same clock.
 */
internal fun pcmDuration(format: AudioFormat, frameCount: Long): Duration {
    if (format.sampleRate <= 0 || frameCount <= 0L) return Duration.ZERO
    val seconds = frameCount / format.sampleRate
    val remainderFrames = frameCount % format.sampleRate
    val remainderMs = (remainderFrames * 1000L) / format.sampleRate
    return (seconds * 1000L + remainderMs).milliseconds
}
