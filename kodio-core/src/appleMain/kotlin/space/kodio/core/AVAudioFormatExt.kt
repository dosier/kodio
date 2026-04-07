package space.kodio.core

import platform.AVFAudio.*
import space.kodio.core.util.namedLogger

private val log = namedLogger("AVAudioFormatExt")

val DefaultAppleRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, signed = true)
)

/**
 * Converts this common [AudioFormat] to an [AVAudioFormat] instance.
 *
 * @throws AVAudioFormatException if conversion is not supported.
 */
@Throws(AVAudioFormatException::class)
fun AudioFormat.toAVAudioFormat(): AVAudioFormat {
    log.info { "toAVAudioFormat(): input=$this" }
    val (commonFormat, interleaved) = when (val e = encoding) {
        is SampleEncoding.PcmInt -> when (e.bitDepth) {
            IntBitDepth.Sixteen -> AVAudioPCMFormatInt16 to (e.layout == SampleLayout.Interleaved)
            IntBitDepth.Eight, IntBitDepth.TwentyFour, IntBitDepth.ThirtyTwo ->
                throw IllegalArgumentException("Use explicit ASBD for ${e.bitDepth.bits}-bit PCM Int on CoreAudio.")
        }
        is SampleEncoding.PcmFloat -> when (e.precision) {
            FloatPrecision.F32 -> AVAudioPCMFormatFloat32 to (e.layout == SampleLayout.Interleaved)
            FloatPrecision.F64 -> AVAudioPCMFormatFloat64 to (e.layout == SampleLayout.Interleaved)
        }
    }

    val result = AVAudioFormat(
        commonFormat = commonFormat,
        sampleRate = sampleRate.toDouble(),
        channels = channels.count.toUInt(),
        interleaved = interleaved
    )
    log.info {
        "toAVAudioFormat(): result commonFormat=$commonFormat, " +
            "sampleRate=${result.sampleRate}, channels=${result.channelCount}, " +
            "interleaved=$interleaved, isStandard=${result.isStandard()}"
    }
    return result
}

/**
 * Converts this [AVAudioFormat] to a common [AudioFormat] instance.
 *
 * @throws AVAudioFormatException if conversion is not supported.
 */
@Throws(AVAudioFormatException::class)
fun AVAudioFormat.toCommonAudioFormat(): AudioFormat {
    log.info {
        "toCommonAudioFormat(): sampleRate=$sampleRate, channels=$channelCount, " +
            "commonFormat=$commonFormat"
    }
    return AudioFormat(
        sampleRate = sampleRate.toInt(),
        channels = Channels.fromInt(channelCount.toInt()),
        encoding = commonFormat.toCommonEncoding(),
    )
}

private fun AVAudioCommonFormat.toCommonEncoding(): SampleEncoding = when (this) {
    AVAudioPCMFormatInt16 -> SampleEncoding.PcmInt(IntBitDepth.Sixteen, Endianness.Little, SampleLayout.Interleaved, true)
    AVAudioPCMFormatFloat32 -> SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
    AVAudioPCMFormatFloat64 -> SampleEncoding.PcmFloat(FloatPrecision.F64, SampleLayout.Interleaved)
    else -> throw AVAudioFormatException.UnknownEncodingForCommonFormat(this)
}
