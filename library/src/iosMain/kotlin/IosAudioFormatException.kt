import platform.AVFAudio.AVAudioCommonFormat

sealed class IosAudioFormatException(message: String) : Exception(message) {

    class UnsupportedBitDepth(bitDepth: BitDepth) : IosAudioFormatException("Unsupported bitDepth: $bitDepth.")
    class UnknownBitDepthForCommonFormat(format: AVAudioCommonFormat): IosAudioFormatException("Unknown bitDepth for format: $format.")
}