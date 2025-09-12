package space.kodio.core

sealed class AndroidAudioFormatException(message: String) : Exception(message) {

    class UnsupportedBitDepth(depth: IntBitDepth) :
        AndroidAudioFormatException("Unsupported PCM bit depth on Android: $depth")

    class UnsupportedEncoding(code: Int) :
        AndroidAudioFormatException("Unsupported Android AudioFormat encoding: $code")

    class UnsupportedCommonEncoding(enc: SampleEncoding) :
        AndroidAudioFormatException("Unsupported/common encoding for Android interop: $enc")
}