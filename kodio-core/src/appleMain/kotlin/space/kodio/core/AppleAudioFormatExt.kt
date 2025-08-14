package space.kodio.core

import platform.AVFAudio.AVAudioCommonFormat
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatFloat64

val DefaultRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    bitDepth = BitDepth.ThirtyTwo,
    channels = Channels.Mono,
    encoding = Encoding.Pcm.Unsigned
)

typealias AppleAudioFormat = AVAudioFormat

/**
 * Converts this common [AudioFormat] to an [AppleAudioFormat] instance.
 *
 * @throws AppleAudioFormatException if conversion is not supported.
 */
@Throws(AppleAudioFormatException::class)
fun AudioFormat.toIosAudioFormat(): AppleAudioFormat {
    // iOS commonly uses Float32 for processing, but we map to PCM formats for raw data.
    val encoding = encoding
    if (encoding !is Encoding.Pcm)
        throw AppleAudioFormatException.UnsupportedCommonEncoding(encoding)
    return AppleAudioFormat(
        commonFormat = bitDepth.toAVAudioCommonFormat(),
        sampleRate = sampleRate.toDouble(),
        channels = channels.count.toUInt(),
        interleaved = false
    )
}

/**
 * Converts this [AppleAudioFormat] to a common [AudioFormat] instance.
 *
 * @throws AppleAudioFormatException if conversion is not supported.
 */
@Throws(AppleAudioFormatException::class)
fun AppleAudioFormat.toCommonAudioFormat(): AudioFormat {
    return AudioFormat(
        sampleRate = sampleRate.toInt(),
        bitDepth = commonFormat.toCommonBitDepth(),
        channels = Channels.fromInt(channelCount.toInt()),
        encoding = commonFormat.toCommonEncoding(),
    )
}

internal fun BitDepth.toAVAudioCommonFormat(): AVAudioCommonFormat = when (this) {
    BitDepth.SixtyFour -> AVAudioPCMFormatFloat64
    BitDepth.ThirtyTwo -> AVAudioPCMFormatFloat32
    else -> throw AppleAudioFormatException.UnsupportedBitDepth(this)
}

internal fun AVAudioCommonFormat.toCommonBitDepth(): BitDepth = when (this) {
    AVAudioPCMFormatFloat64 -> BitDepth.SixtyFour
    AVAudioPCMFormatFloat32 -> BitDepth.ThirtyTwo
    else -> throw AppleAudioFormatException.UnknownBitDepthForCommonFormat(this)
}

internal fun AVAudioCommonFormat.toCommonEncoding(): Encoding = when (this) {
    AVAudioPCMFormatFloat64,
    AVAudioPCMFormatFloat32 -> Encoding.Pcm.Unsigned
    else -> throw AppleAudioFormatException.UnknownEncodingForCommonFormat(this)
}