package space.kodio.core.io

import platform.CoreAudioTypes.AudioStreamBasicDescription

sealed class AVAudioBufferException(message: String) : Exception(message) {

    class MissingStreamDescription : AVAudioBufferException("The stream description is missing.")
    class InvalidStreamDescription(description: AudioStreamBasicDescription) : AVAudioBufferException("The stream description is invalid. (mBytesPerFrame (=${description.mBytesPerFrame}), must be > 0)")
    class MissingAudioBufferList : AVAudioBufferException("The audio buffer list is missing.")
}