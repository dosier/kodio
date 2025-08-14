package space.kodio.core

import platform.AVFAudio.AVAudioCommonFormat

sealed class AppleAudioFormatException(message: String) : Exception(message) {

    class UnsupportedBitDepth(bitDepth: BitDepth) : AppleAudioFormatException("Unsupported bitDepth: $bitDepth.")

    class UnknownBitDepthForCommonFormat(format: AVAudioCommonFormat): AppleAudioFormatException("Unknown bitDepth for format: $format.")

    class UnsupportedCommonEncoding(encoding: Encoding) : AppleAudioFormatException("Unsupported encoding: $encoding.")

    class UnknownEncodingForCommonFormat(format: AVAudioCommonFormat): AppleAudioFormatException("Unknown encoding for format: $format.")
}