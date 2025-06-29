import platform.AVFAudio.AVAudioFormat
import platform.CoreAudioTypes.kAudioFormatLinearPCM

/**
 * Converts our common AudioFormat to an Apple AVAudioFormat.
 * Note: Assumes PCM format. The bitDepth is used to determine the correct format constant.
 */
fun AudioFormat.toAVAudioFormat(): AVAudioFormat? {
    // iOS commonly uses Float32 for processing, but we map to PCM formats for raw data.
    val format = when (bitDepth) {
        32 -> kAudioFormatLinearPCM
        16 -> kAudioFormatLinearPCM
        8 -> kAudioFormatLinearPCM
        else -> return null // Unsupported bit depth
    }.toULong()
    return AVAudioFormat(format, sampleRate.toDouble(), channels.toUInt(), false)
}
