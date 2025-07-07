import android.media.AudioFormat as AndroidAudioFormat

internal fun Int.toAndroidChannelInMask(): Int {
    return when (this) {
        1 -> AndroidAudioFormat.CHANNEL_IN_MONO
        2 -> AndroidAudioFormat.CHANNEL_IN_STEREO
        else -> error("Unsupported channel count: $this")
    }
}

internal fun Int.toAndroidChannelOutMask(): Int {
    return when (this) {
        1 -> AndroidAudioFormat.CHANNEL_OUT_MONO
        2 -> AndroidAudioFormat.CHANNEL_OUT_STEREO
        else -> error("Unsupported channel count: $this")
    }
}

internal fun Int.toAndroidFormatEncoding(): Int {
    return when (this) {
        8 -> AndroidAudioFormat.ENCODING_PCM_8BIT
        16 -> AndroidAudioFormat.ENCODING_PCM_16BIT
        32 -> AndroidAudioFormat.ENCODING_PCM_FLOAT
        else -> error("Unsupported bit depth: $this")
    }
}
