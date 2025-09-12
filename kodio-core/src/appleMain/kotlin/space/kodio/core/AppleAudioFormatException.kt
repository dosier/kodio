package space.kodio.core

import platform.AVFAudio.AVAudioCommonFormat

sealed class AppleAudioFormatException(message: String) : Exception(message) {

    class UnknownEncodingForCommonFormat(format: AVAudioCommonFormat): AppleAudioFormatException("Unknown encoding for format: $format.")

    class NoStreamDescription(format: AudioFormat) : AppleAudioFormatException("No stream description for format: $format.")
}