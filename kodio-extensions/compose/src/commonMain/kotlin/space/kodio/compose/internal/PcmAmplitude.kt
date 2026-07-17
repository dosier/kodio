package space.kodio.compose.internal

import space.kodio.core.AudioFormat
import space.kodio.core.Endianness
import space.kodio.core.FloatPrecision
import space.kodio.core.SampleEncoding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Absolute amplitude of a single sample at [offset], normalized to `0f..1f`.
 *
 * Honors PCM integer bit depth / signedness / endianness and float32/64.
 */
internal fun sampleAbsoluteAmplitude(
    bytes: ByteArray,
    offset: Int,
    encoding: SampleEncoding,
): Float {
    return when (encoding) {
        is SampleEncoding.PcmInt -> {
            val bits = encoding.bitDepth.bits
            val bps = bits / 8
            if (offset < 0 || offset + bps > bytes.size) return 0f
            val raw = readPcmIntBits(bytes, offset, bps, encoding.endianness)
            val centered = if (encoding.signed) {
                signExtend(raw, bits)
            } else {
                raw - (1L shl (bits - 1))
            }
            val divisor = (1L shl (bits - 1)).toFloat()
            abs(centered.toFloat() / divisor).coerceIn(0f, 1f)
        }
        is SampleEncoding.PcmFloat -> when (encoding.precision) {
            FloatPrecision.F32 -> {
                if (offset + 4 > bytes.size) return 0f
                abs(Float.fromBits(readInt32Le(bytes, offset))).coerceIn(0f, 1f)
            }
            FloatPrecision.F64 -> {
                if (offset + 8 > bytes.size) return 0f
                abs(Double.fromBits(readInt64Le(bytes, offset)).toFloat()).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * RMS amplitude of a PCM [chunk] using [format]'s encoding (not PCM16-LE-only).
 */
internal fun calculateRmsAmplitude(chunk: ByteArray, format: AudioFormat): Float {
    if (chunk.isEmpty()) return 0f
    val bytesPerSample = format.bytesPerSample.coerceAtLeast(1)
    if (chunk.size < bytesPerSample) return 0f

    var sumSquares = 0.0
    var count = 0
    var offset = 0
    while (offset + bytesPerSample <= chunk.size) {
        val amp = sampleAbsoluteAmplitude(chunk, offset, format.encoding)
        sumSquares += amp.toDouble() * amp.toDouble()
        count++
        offset += bytesPerSample
    }
    return if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
}

/**
 * Peak amplitudes for [barCount] evenly spaced windows over interleaved PCM bytes.
 * Values are normalized to the track peak so quiet material still fills the view.
 */
internal fun peakAmplitudes(
    bytes: ByteArray,
    format: AudioFormat,
    barCount: Int,
    minFloor: Float = 0.04f,
): List<Float> {
    if (bytes.isEmpty() || barCount <= 0) return emptyList()

    val bytesPerSample = format.bytesPerSample.coerceAtLeast(1)
    val channelCount = format.channels.count.coerceAtLeast(1)
    val bytesPerFrame = format.bytesPerFrame.coerceAtLeast(bytesPerSample)
    val totalFrames = bytes.size / bytesPerFrame
    if (totalFrames == 0) return List(barCount) { 0f }

    val framesPerBar = (totalFrames / barCount).coerceAtLeast(1)
    val amplitudes = ArrayList<Float>(barCount)

    for (bar in 0 until barCount) {
        val startFrame = bar * framesPerBar
        val endFrame = (startFrame + framesPerBar).coerceAtMost(totalFrames)
        var peak = 0f
        for (frame in startFrame until endFrame) {
            val frameOffset = frame * bytesPerFrame
            for (ch in 0 until channelCount) {
                val sampleOffset = frameOffset + ch * bytesPerSample
                peak = max(peak, sampleAbsoluteAmplitude(bytes, sampleOffset, format.encoding))
            }
        }
        amplitudes.add(peak)
    }

    val maxPeak = amplitudes.maxOrNull()?.takeIf { it > 0f } ?: 1f
    return amplitudes.map { amp ->
        (amp / maxPeak).coerceIn(minFloor, 1f)
    }
}

private fun readPcmIntBits(bytes: ByteArray, offset: Int, bps: Int, endianness: Endianness): Long {
    var value = 0L
    when (endianness) {
        Endianness.Little -> {
            for (i in 0 until bps) {
                value = value or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
            }
        }
        Endianness.Big -> {
            for (i in 0 until bps) {
                value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
        }
    }
    return value
}

private fun signExtend(value: Long, bits: Int): Long {
    val signBit = 1L shl (bits - 1)
    return if (value and signBit != 0L) {
        value or (-1L shl bits)
    } else {
        value
    }
}

private fun readInt32Le(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun readInt64Le(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
        value = value or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
    }
    return value
}
