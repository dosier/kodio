import javax.sound.sampled.AudioFormat as JvmAudioFormat

fun AudioFormat.toJvmAudioFormat(): JvmAudioFormat {
    return JvmAudioFormat(this.sampleRate.toFloat(), this.bitDepth, this.channels, true, false)
}
