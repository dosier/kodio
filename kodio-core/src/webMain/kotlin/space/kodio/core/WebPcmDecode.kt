package space.kodio.core

import web.audio.AudioBuffer
import web.audio.AudioContext

/**
 * Shared web-target helpers backing [WebAudioPlaybackSession]'s playback path.
 *
 * By the time these are invoked, [BaseAudioPlaybackSession.play] has already piped
 * the source [AudioFlow] through `convertAudio`, normalizing it to interleaved
 * Float32 LE (see [WebAudioPlaybackSession.preparePlayback]). That means there is
 * no PCM-int / endianness handling to do here - just reinterpret the bytes,
 * split them into per-channel buffers, and hand each channel to Web Audio.
 *
 * The only piece left to the JS / Wasm targets is [toJsFloat32Array], which builds
 * a JS-heap [js.typedarrays.Float32Array] from a Kotlin [FloatArray].
 */

/**
 * Builds a Web Audio [AudioBuffer] from interleaved Float32 LE bytes.
 *
 * `format` describes the byte stream that has been normalized by `convertAudio`,
 * so its encoding is always interleaved Float32. Only the channel count and
 * sample rate are read from it here.
 */
internal fun AudioContext.createBufferFrom(format: AudioFormat, data: ByteArray): AudioBuffer {
    val channelCount = format.channels.count
    val interleaved = data.toFloatArrayLE()
    val frameCount = interleaved.size / channelCount
    val perChannel = interleaved.deinterleave(channelCount)

    val buffer = createBuffer(
        numberOfChannels = channelCount,
        length = frameCount,
        sampleRate = format.sampleRate.toFloat()
    )
    for (c in 0 until channelCount) {
        buffer.copyToChannel(perChannel[c].toJsFloat32Array(), c)
    }
    return buffer
}

/**
 * Reinterprets a little-endian IEEE-754 Float32 byte stream as a [FloatArray].
 * Mirrors the existing `bytesToFloatArrayLE` in `AndroidAudioPlaybackSession`
 * for the equivalent Float32 playback path on Android.
 */
internal fun ByteArray.toFloatArrayLE(): FloatArray {
    require(size % 4 == 0) { "PCM Float32 byte length must be multiple of 4 (got $size)." }
    val out = FloatArray(size / 4)
    var i = 0
    var j = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = this[i + 1].toInt() and 0xFF
        val b2 = this[i + 2].toInt() and 0xFF
        val b3 = this[i + 3].toInt() and 0xFF
        val bits = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        out[j++] = Float.fromBits(bits)
        i += 4
    }
    return out
}

/**
 * Splits an interleaved sample buffer (`[L0, R0, L1, R1, ...]`) into one
 * [FloatArray] per channel (`[[L0, L1, ...], [R0, R1, ...]]`).
 *
 * For mono input this returns a single-element array wrapping the original data
 * (no copy), since deinterleaving is a no-op.
 */
internal fun FloatArray.deinterleave(channels: Int): Array<FloatArray> {
    require(channels > 0) { "Channel count must be positive (got $channels)." }
    require(size % channels == 0) {
        "Interleaved buffer size $size is not aligned to $channels channels."
    }
    if (channels == 1) return arrayOf(this)

    val frames = size / channels
    val out = Array(channels) { FloatArray(frames) }
    for (i in 0 until frames) {
        val base = i * channels
        for (c in 0 until channels) {
            out[c][i] = this[base + c]
        }
    }
    return out
}
