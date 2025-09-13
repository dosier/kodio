package space.kodio.core

import platform.AVFAudio.AVAudioCommonFormat

sealed class AVAudioFormatException(message: String) : Exception(message) {

    class UnknownEncodingForCommonFormat(format: AVAudioCommonFormat): AVAudioFormatException("Unknown encoding for format: $format.")

    class NoStreamDescription(format: AudioFormat) : AVAudioFormatException("No stream description for format: $format.")
}