import platform.AVFAudio.AVAudioCommonFormat
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatFloat64

val DefaultIosRecordingAudioFormat = AudioFormat(44100, 32, 1)

/**
 * Converts our common AudioFormat to an Apple AVAudioFormat.
 */
fun AudioFormat.toIosAudioFormat(): AVAudioFormat? {
    // iOS commonly uses Float32 for processing, but we map to PCM formats for raw data.
    val commonFormat = bitDepthToAVAudioCommonFormat(bitDepth)?:return null
    return AVAudioFormat(commonFormat, sampleRate.toDouble(), channels.toUInt(), false)
}

/**
 * Converts an AVAudioFormat to our common AudioFormat.
 */
fun AVAudioFormat.toCommonAudioFormat(): AudioFormat {
    return AudioFormat(
        sampleRate = sampleRate.toInt(),
        bitDepth = bitDepthFromAVAudioCommonFormat(commonFormat),
        channels = channelCount.toInt()
    )
}

fun bitDepthToAVAudioCommonFormat(bitDepth: Int): AVAudioCommonFormat? {
    return when (bitDepth) {
        64 -> AVAudioPCMFormatFloat64
        32 -> AVAudioPCMFormatFloat32
        else -> null
    }
}

fun bitDepthFromAVAudioCommonFormat(commonFormat: AVAudioCommonFormat): Int {
    return when (commonFormat) {
        AVAudioPCMFormatFloat64 -> 64
        AVAudioPCMFormatFloat32 -> 32
        else -> 0
    }
}