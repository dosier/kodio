import android.media.AudioFormat as AndroidAudioFormat

internal fun Channels.toAndroidChannelInMask(): Int {
    return when (this) {
        Channels.Mono -> AndroidAudioFormat.CHANNEL_IN_MONO
        Channels.Stereo -> AndroidAudioFormat.CHANNEL_IN_STEREO
    }
}

internal fun Channels.toAndroidChannelOutMask(): Int {
    return when (this) {
        Channels.Mono -> AndroidAudioFormat.CHANNEL_OUT_MONO
        Channels.Stereo -> AndroidAudioFormat.CHANNEL_OUT_STEREO
    }
}

internal fun BitDepth.toAndroidFormatEncoding(): Int {
    return when (this) {
        BitDepth.Eight -> AndroidAudioFormat.ENCODING_PCM_8BIT
        BitDepth.Sixteen -> AndroidAudioFormat.ENCODING_PCM_16BIT
        BitDepth.ThirtyTwo -> AndroidAudioFormat.ENCODING_PCM_FLOAT
        else -> throw AndroidAudioFormatException.UnsupportedBitDepth(this)
    }
}
