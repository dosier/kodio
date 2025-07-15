package gg.kodio.core

import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.AudioFormat as JvmAudioFormat

internal val DefaultJvmRecordingAudioFormat = AudioFormat(
    sampleRate = 44100,
    bitDepth = BitDepth.Sixteen,
    channels = Channels.Mono,
    encoding = Encoding.Pcm.Signed,
    endianness = Endianness.Little
)

internal fun AudioFormat.toJvmAudioFormat(): JvmAudioFormat =
    JvmAudioFormat(
        /* sampleRate = */ sampleRate.toFloat(),
        /* sampleSizeInBits = */ bitDepth.value,
        /* channels = */ channels.count,
        /* signed = */ when(val encoding = encoding) {
            is Encoding.Pcm -> encoding.signed
            else -> throw JvmAudioException.UnsupportedCommonEncoding(encoding)
        },
        /* bigEndian = */ endianness == Endianness.Big
    )

/**
 * Helper function to determine supported formats for a given line type on a mixer.
 * This implementation directly queries the DataLine.Info for its supported formats.
 */
internal fun Array<Line.Info>.toCommonAudioFormats() = filterIsInstance<DataLine.Info>()
    .flatMap { dataLineInfo ->
        dataLineInfo.formats.mapNotNull { jvmFormat ->
            runCatching {
                AudioFormat(
                    sampleRate = ensureSampleRateValid(jvmFormat.sampleRate.toInt()),
                    bitDepth = BitDepth.fromInt(jvmFormat.sampleSizeInBits),
                    channels = Channels.fromInt(jvmFormat.channels),
                    encoding = jvmFormat.encoding.toCommonEncoding(),
                    endianness = if (jvmFormat.isBigEndian) Endianness.Big else Endianness.Little
                )
            }.getOrNull()
        }
    }
    .distinct() // Remove any duplicate formats

private fun ensureSampleRateValid(sampleRate: Int): Int {
    require(sampleRate in 8000..96000) {
        "Sample rate must be between 8kHz and 96kHz"
    }
    return sampleRate
}

private fun JvmAudioFormat.Encoding.toCommonEncoding() = when (this) {
    JvmAudioFormat.Encoding.PCM_SIGNED -> Encoding.Pcm.Signed
    JvmAudioFormat.Encoding.PCM_UNSIGNED -> Encoding.Pcm.Unsigned
    else -> throw JvmAudioException.UnsupportedJvmEncoding(this)
}