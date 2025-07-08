sealed class AndroidAudioFormatException(message: String) : Exception(message) {

    class UnsupportedBitDepth(bitDepth: BitDepth) : AndroidAudioFormatException("Unsupported bitDepth: $bitDepth.")
}