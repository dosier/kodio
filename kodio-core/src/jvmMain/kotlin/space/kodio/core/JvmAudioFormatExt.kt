package space.kodio.core

import javax.sound.sampled.AudioFormat as JvmAudioFormat
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line

/* ---------- Defaults ---------- */

internal val DefaultJvmRecordingAudioFormat = AudioFormat(
    sampleRate = 44100,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(
        bitDepth = IntBitDepth.Sixteen,
        endianness = Endianness.Little,
        layout = SampleLayout.Interleaved,
        signed = true,
        packed = true
    )
)

/* ---------- To JVM ---------- */

internal fun AudioFormat.toJvmAudioFormat(): JvmAudioFormat {
    // JavaSound does not support planar buffers in a single line format
    val interleaved = when (val e = encoding) {
        is SampleEncoding.PcmInt   -> e.layout == SampleLayout.Interleaved
        is SampleEncoding.PcmFloat -> e.layout == SampleLayout.Interleaved
    }
    if (!interleaved) throw JvmAudioException.UnsupportedCommonEncoding(encoding)

    val bigEndian = when (val e = encoding) {
        is SampleEncoding.PcmInt   -> e.endianness == Endianness.Big
        is SampleEncoding.PcmFloat -> false // JavaSound PCM_FLOAT is defined little-endian in practice
    }

    val (enc, bits) = when (val e = encoding) {
        is SampleEncoding.PcmInt -> {
            val enc = if (e.signed) JvmAudioFormat.Encoding.PCM_SIGNED
            else JvmAudioFormat.Encoding.PCM_UNSIGNED
            enc to e.bitDepth.bits
        }
        is SampleEncoding.PcmFloat -> {
            val bits = when (e.precision) {
                FloatPrecision.F32 -> 32
                FloatPrecision.F64 -> 64
            }
            JvmAudioFormat.Encoding.PCM_FLOAT to bits
        }
    }

    val bytesPerSample = bits / 8
    val frameSize = bytesPerSample * channels.count

    // Use the full constructor so we can set Encoding + frameSize explicitly.
    return JvmAudioFormat(
        /* encoding         = */ enc,
        /* sampleRate       = */ sampleRate.toFloat(),
        /* sampleSizeInBits = */ bits,
        /* channels         = */ channels.count,
        /* frameSize        = */ frameSize,
        /* frameRate        = */ sampleRate.toFloat(),
        /* bigEndian        = */ bigEndian
    )
}

/* ---------- From JVM (supported formats enumeration) ---------- */

internal fun Array<Line.Info>.toCommonAudioFormats(): List<AudioFormat> =
    filterIsInstance<DataLine.Info>()
        .flatMap { info ->
            info.formats.mapNotNull { jvm ->
                runCatching { jvm.toCommonAudioFormatOrNull() }.getOrNull()
            }
        }
        .distinct()

private fun JvmAudioFormat.toCommonAudioFormatOrNull(): AudioFormat? {
    val sr = ensureSampleRateValid(sampleRate.toInt())
    val ch = channels
    val channelsCommon = try { Channels.fromInt(ch) } catch (_: Throwable) { return null }

    // Interleaving only; JavaSound formats are interleaved for PCM data lines
    val layout = SampleLayout.Interleaved

    val enc: SampleEncoding = when (encoding) {
        JvmAudioFormat.Encoding.PCM_SIGNED -> {
            val depth = intBitDepthOrNull(sampleSizeInBits) ?: return null
            SampleEncoding.PcmInt(
                bitDepth = depth,
                endianness = if (isBigEndian) Endianness.Big else Endianness.Little,
                layout = layout,
                signed = true,
                packed = true
            )
        }
        JvmAudioFormat.Encoding.PCM_UNSIGNED -> {
            val depth = intBitDepthOrNull(sampleSizeInBits) ?: return null
            SampleEncoding.PcmInt(
                bitDepth = depth,
                endianness = if (isBigEndian) Endianness.Big else Endianness.Little,
                layout = layout,
                signed = false,
                packed = true
            )
        }
        JvmAudioFormat.Encoding.PCM_FLOAT -> {
            // JavaSound commonly exposes 32-bit float; some JVMs may allow 64, keep it if present
            val precision = when (sampleSizeInBits) {
                32 -> FloatPrecision.F32
                64 -> FloatPrecision.F64
                else -> return null
            }
            // JavaSound PCM_FLOAT is effectively little-endian on common mixers; bigEndian flag is ignored
            SampleEncoding.PcmFloat(
                precision = precision,
                layout = layout
            )
        }
        else -> return null
    }

    return AudioFormat(
        sampleRate = sr,
        channels = channelsCommon,
        encoding = enc
    )
}

/* ---------- Helpers ---------- */

private fun ensureSampleRateValid(sampleRate: Int): Int {
    require(sampleRate in 8000..192000) { "Sample rate must be between 8 kHz and 192 kHz" }
    return sampleRate
}

private fun intBitDepthOrNull(bits: Int): IntBitDepth? = when (bits) {
    8  -> IntBitDepth.Eight
    16 -> IntBitDepth.Sixteen
    24 -> IntBitDepth.TwentyFour
    32 -> IntBitDepth.ThirtyTwo
    else -> null
}