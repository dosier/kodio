package gg.kodio.core

import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem

// AudioSystem extensions

internal fun getMixer(device: AudioDevice) : Mixer {
    val mixers = JvmAudioSystem.getMixerInfo()
        .filter { it.name == device.id }
        .map { JvmAudioSystem.getMixer(it) }
    return when(device) {
        is AudioDevice.Input -> mixers.firstOrNull { it.isLineSupported(DataLine.Info(TargetDataLine::class.java, null)) }
        is AudioDevice.Output -> mixers.firstOrNull { it.isLineSupported(DataLine.Info(SourceDataLine::class.java, null)) }
    } ?: throw JvmAudioException.DeviceNotFound(device)
}

// Mixer extensions

internal inline fun<reified T : DataLine> Mixer.getLine(format: AudioFormat) : T {
    try {

        val jvmFormat = format.toJvmAudioFormat()
        val lineInfo = DataLine.Info(T::class.java, jvmFormat)
        return getLine(lineInfo) as T
    } catch (e: LineUnavailableException) {
        throw JvmAudioException.LineNotAvailable(e)
    }
}

internal inline fun<reified T : DataLine> Mixer.isSupported(format: AudioFormat) =
    isLineSupported(DataLine.Info(T::class.java, format.toJvmAudioFormat()))

// DataLine extensions

internal inline fun<reified T : DataLine> T.open(format: AudioFormat) =
    when(this) {
        is TargetDataLine -> { open(format.toJvmAudioFormat()) }
        is SourceDataLine -> { open(format.toJvmAudioFormat()) }
        else -> throw JvmAudioException.UnsupportedLineType(T::class)
    }

