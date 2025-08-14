package space.kodio.core.io

import platform.CoreAudioTypes.AudioStreamBasicDescription

sealed class AppleAudioBufferException(message: String) : Exception(message) {

    class MissingStreamDescription : AppleAudioBufferException("The stream description is missing.")
    class InvalidStreamDescription(description: AudioStreamBasicDescription) : AppleAudioBufferException("The stream description is invalid. (mBytesPerFrame (=${description.mBytesPerFrame}), must be > 0)")
    class MissingAudioBufferList : AppleAudioBufferException("The audio buffer list is missing.")
}