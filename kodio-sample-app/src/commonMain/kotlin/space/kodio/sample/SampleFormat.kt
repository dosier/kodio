package space.kodio.sample

import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.FloatPrecision
import space.kodio.core.SampleEncoding
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import kotlin.time.Duration

internal fun containerLabel(format: AudioFileFormat): String = when (format) {
    is AudioFileFormat.Wav -> "WAV"
    is AudioFileFormat.Aiff -> "AIFF"
    is AudioFileFormat.Au -> "AU"
    is AudioFileFormat.Mp3 -> "MP3"
}

internal fun encodingLabel(encoding: AudioEncodingInfo): String = when (encoding) {
    is AudioEncodingInfo.Pcm -> when (val pcm = encoding.encoding) {
        is SampleEncoding.PcmInt -> "${pcm.bitDepth.bits}-bit PCM"
        is SampleEncoding.PcmFloat ->
            "${if (pcm.precision == FloatPrecision.F32) "32" else "64"}-bit float"
    }
    is AudioEncodingInfo.Compressed.Mp3 -> "MP3"
}

internal fun encodingDescription(encoding: SampleEncoding): String = when (encoding) {
    is SampleEncoding.PcmInt -> "${encoding.bitDepth.bits}-bit PCM"
    is SampleEncoding.PcmFloat ->
        "${if (encoding.precision == FloatPrecision.F32) "32" else "64"}-bit Float"
}

internal fun formatSampleRate(rate: Int): String = when {
    rate >= 1000 -> "${formatDecimal(rate / 1000.0, 1)} kHz"
    else -> "$rate Hz"
}

internal fun formatDurationShort(duration: Duration?): String {
    if (duration == null) return "—"
    val totalMs = duration.inWholeMilliseconds
    val seconds = totalMs / 1000
    val ms = totalMs % 1000
    return if (seconds > 0) "${seconds}.${(ms / 100)}s" else "${ms}ms"
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${formatDecimal(bytes / 1_048_576.0, 1)} MB"
    bytes >= 1024 -> "${formatDecimal(bytes / 1024.0, 1)} KB"
    else -> "$bytes B"
}

internal fun formatPlaybackTime(duration: Duration): String {
    if (duration <= Duration.ZERO) return "0:00"
    val totalMs = duration.inWholeMilliseconds.coerceAtLeast(0)
    val totalSeconds = totalMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (totalMs % 1000) / 100
    return if (minutes >= 10) {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
    }
}

internal val AudioFormat.label: String
    get() = buildString {
        append("${sampleRate / 1000.0}kHz")
        append(" / ")
        append(if (channels == Channels.Mono) "Mono" else "Stereo")
        append(" / ")
        when (val enc = encoding) {
            is SampleEncoding.PcmInt -> append("${enc.bitDepth.bits}-bit int")
            is SampleEncoding.PcmFloat -> append(
                "${if (enc.precision == FloatPrecision.F32) "32" else "64"}-bit float",
            )
        }
    }
