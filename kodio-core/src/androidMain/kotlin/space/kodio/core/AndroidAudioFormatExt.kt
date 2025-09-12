package space.kodio.core

import android.os.Build
import android.media.AudioFormat as AndroidAudioFormat

/* ---------------- Defaults ---------------- */

val DefaultAndroidRecordingAudioFormat = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(
        bitDepth = IntBitDepth.Sixteen,
        endianness = Endianness.Little,
        layout = SampleLayout.Interleaved,
        signed = true,
        packed = true
    )
)

/* ---------------- Channel masks ---------------- */

internal fun Channels.toAndroidChannelInMask(): Int = when (this) {
    Channels.Mono   -> AndroidAudioFormat.CHANNEL_IN_MONO
    Channels.Stereo -> AndroidAudioFormat.CHANNEL_IN_STEREO
}

internal fun Channels.toAndroidChannelOutMask(): Int = when (this) {
    Channels.Mono   -> AndroidAudioFormat.CHANNEL_OUT_MONO
    Channels.Stereo -> AndroidAudioFormat.CHANNEL_OUT_STEREO
}


/* ---------------- From Android -> common ---------------- */

internal fun AndroidAudioFormat.toCommonAudioFormat(): AudioFormat {
    val ch = Channels.fromInt(channelCount)

    val enc: SampleEncoding = when (encoding) {
        AndroidAudioFormat.ENCODING_PCM_8BIT -> SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Eight,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = false,        // Android 8-bit is effectively UNSIGNED
            packed = true
        )
        AndroidAudioFormat.ENCODING_PCM_16BIT -> SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
            packed = true
        )
        AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED -> SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.TwentyFour,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
            packed = true
        )
        AndroidAudioFormat.ENCODING_PCM_32BIT -> SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.ThirtyTwo,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
            packed = true
        )
        AndroidAudioFormat.ENCODING_PCM_FLOAT -> SampleEncoding.PcmFloat(
            precision = FloatPrecision.F32,
            layout = SampleLayout.Interleaved
        )
        else -> throw AndroidAudioFormatException.UnsupportedEncoding(encoding)
    }

    return AudioFormat(
        sampleRate = sampleRate,
        channels = ch,
        encoding = enc
    )
}

/* ---------------- To Android -> input/output variants ---------------- */

internal fun AudioFormat.toAndroidInputAudioFormat(): AndroidAudioFormat {
    val enc = toAndroidEncoding()
    ensureAndroidInterleaved(this)

    return AndroidAudioFormat.Builder()
        .setEncoding(enc)
        .setSampleRate(sampleRate)
        .setChannelMask(channels.toAndroidChannelInMask())
        .build()
}

internal fun AudioFormat.toAndroidOutputAudioFormat(): AndroidAudioFormat {
    val enc = toAndroidEncoding()
    ensureAndroidInterleaved(this)

    return AndroidAudioFormat.Builder()
        .setEncoding(enc)
        .setSampleRate(sampleRate)
        .setChannelMask(channels.toAndroidChannelOutMask())
        .build()
}


/** Map common format -> Android encoding (kept in one place). */
internal fun AudioFormat.toAndroidEncoding(): Int = when (val e = encoding) {
    is SampleEncoding.PcmInt -> when (e.bitDepth) {
        IntBitDepth.Eight      -> AndroidAudioFormat.ENCODING_PCM_8BIT
        IntBitDepth.Sixteen    -> AndroidAudioFormat.ENCODING_PCM_16BIT
        IntBitDepth.TwentyFour -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED
        else
            throw AndroidAudioFormatException.UnsupportedBitDepth(e.bitDepth)
        IntBitDepth.ThirtyTwo  -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            AndroidAudioFormat.ENCODING_PCM_32BIT
        else
            throw AndroidAudioFormatException.UnsupportedBitDepth(e.bitDepth)
    }
    is SampleEncoding.PcmFloat -> when (e.precision) {
        FloatPrecision.F32 -> AndroidAudioFormat.ENCODING_PCM_FLOAT
        FloatPrecision.F64 -> throw IllegalArgumentException("Android does not support PCM Float64 in AudioTrack.")
    }
}

/* ---------------- Guards & exceptions ---------------- */

private fun ensureAndroidInterleaved(fmt: AudioFormat) {
    val interleaved = when (val e = fmt.encoding) {
        is SampleEncoding.PcmInt   -> e.layout == SampleLayout.Interleaved
        is SampleEncoding.PcmFloat -> e.layout == SampleLayout.Interleaved
    }
    require(interleaved) { "Android AudioTrack/AudioRecord require interleaved PCM frames." }
}