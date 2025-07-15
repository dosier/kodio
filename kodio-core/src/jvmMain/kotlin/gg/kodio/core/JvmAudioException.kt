package gg.kodio.core

import javax.sound.sampled.AudioFormat as JvmAudioFormat
import javax.sound.sampled.LineUnavailableException
import kotlin.reflect.KClass

sealed class JvmAudioException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data class DeviceNotFound(val device: AudioDevice) : JvmAudioException("Device not found: $device.")

    data class LineNotAvailable(val error: LineUnavailableException) : JvmAudioException("Line not available: ${error.message}.", error)

    data class UnsupportedLineType(val lineClass: KClass<*>) : JvmAudioException("Unsupported line type: $lineClass.")

    data class UnsupportedJvmEncoding(val encoding: JvmAudioFormat.Encoding) : JvmAudioException("Unsupported encoding: $encoding.")

    data class UnsupportedCommonEncoding(val encoding: Encoding) : JvmAudioException("Unsupported common encoding: $encoding.")
}