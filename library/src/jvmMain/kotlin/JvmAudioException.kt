import javax.sound.sampled.LineUnavailableException

sealed class JvmAudioException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data class DeviceNotFound(val device: AudioDevice) : JvmAudioException("Device not found: $device.")

    data class LineNotAvailable(val error: LineUnavailableException) : JvmAudioException("Line not available: ${error.message}.", error)
}