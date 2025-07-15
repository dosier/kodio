package gg.kodio.core

sealed class AndroidAudioFormatException(message: String) : Exception(message) {

    class UnsupportedBitDepth(bitDepth: BitDepth) : AndroidAudioFormatException("Unsupported bitDepth: $bitDepth.")

    class UnsupportedEncoding(encoding: Int) : AndroidAudioFormatException("Unsupported encoding: $encoding.")
}