import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.AudioFormat as JvmAudioFormat

internal fun AudioFormat.toJvmAudioFormat(): JvmAudioFormat {
    return JvmAudioFormat(this.sampleRate.toFloat(), this.bitDepth, this.channels, true, false)
}

/**
 * Helper function to determine supported formats for a given line type on a mixer.
 * This implementation directly queries the DataLine.Info for its supported formats.
 */
internal fun Array<Line.Info>.toCommonAudioFormats() = filterIsInstance<DataLine.Info>()
    .flatMap {
        it.formats.map { jvmFormat ->
            AudioFormat(
                sampleRate = jvmFormat.sampleRate.toInt(),
                bitDepth = jvmFormat.sampleSizeInBits,
                channels = jvmFormat.channels
            )
        }
    }
    // Filter out formats with unspecified values (which can be returned as -1)
    .filter { it.bitDepth > 0 && it.channels > 0 && it.sampleRate > 0 }
    .distinct() // Remove any duplicate formats