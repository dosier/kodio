package space.kodio.core

import platform.AVFAudio.AVAudioCommonFormat
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatFloat64

val DefaultIosRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    bitDepth = BitDepth.ThirtyTwo,
    channels = Channels.Mono,
    encoding = Encoding.Pcm.Unsigned
)

/**
 * Converts our common AudioFormat to an Apple AVAudioFormat.
 */
fun AudioFormat.toIosAudioFormat(): AVAudioFormat {
    // iOS commonly uses Float32 for processing, but we map to PCM formats for raw data.
    val encoding = encoding
    if(encoding !is Encoding.Pcm)
        throw IosAudioFormatException.UnsupportedCommonEncoding(encoding)
    return AVAudioFormat(
        commonFormat = bitDepth.toAVAudioCommonFormat(),
        sampleRate = sampleRate.toDouble(),
        channels = channels.count.toUInt(),
        interleaved = false
    )
}

/**
 * Converts an AVAudioFormat to our common AudioFormat.
 */
fun AVAudioFormat.toCommonAudioFormat(): AudioFormat {
    return AudioFormat(
        sampleRate = sampleRate.toInt(),
        bitDepth = commonFormat.toCommonBitDepth(),
        channels = Channels.fromInt(channelCount.toInt()),
        encoding = commonFormat.toCommonEncoding(),
    )
}

fun BitDepth.toAVAudioCommonFormat(): AVAudioCommonFormat {
    return when (this) {
        BitDepth.SixtyFour -> AVAudioPCMFormatFloat64
        BitDepth.ThirtyTwo -> AVAudioPCMFormatFloat32
        else -> throw IosAudioFormatException.UnsupportedBitDepth(this)
    }
}

fun AVAudioCommonFormat.toCommonBitDepth(): BitDepth {
    return when (this) {
        AVAudioPCMFormatFloat64 -> BitDepth.SixtyFour
        AVAudioPCMFormatFloat32 -> BitDepth.ThirtyTwo
        else -> throw IosAudioFormatException.UnknownBitDepthForCommonFormat(this)
    }
}

fun AVAudioCommonFormat.toCommonEncoding(): Encoding {
    return when (this) {
        AVAudioPCMFormatFloat64,
        AVAudioPCMFormatFloat32 -> Encoding.Pcm.Unsigned
        else -> throw IosAudioFormatException.UnknownEncodingForCommonFormat(this)
    }
}