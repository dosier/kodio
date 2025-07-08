import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.AudioFormat as JvmAudioFormat

internal val DefaultJvmRecordingAudioFormat = AudioFormat(44100, BitDepth.Sixteen, Channels.Mono)

internal fun AudioFormat.toJvmAudioFormat(): JvmAudioFormat =
    JvmAudioFormat(
        /* sampleRate = */ sampleRate.toFloat(),
        /* sampleSizeInBits = */ bitDepth.value,
        /* channels = */ channels.count,
        /* signed = */ true,
        /* bigEndian = */ false
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
                    sampleRate = jvmFormat.sampleRate.toInt(),
                    bitDepth = BitDepth.fromInt(jvmFormat.sampleSizeInBits),
                    channels = Channels.fromInt(jvmFormat.channels)
                )
            }.getOrNull()
        }
    }
    .distinct() // Remove any duplicate formats