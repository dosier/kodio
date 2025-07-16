package space.kodio.core

import platform.CoreAudioTypes.AudioStreamBasicDescription

sealed class IosAudioBufferException(message: String) : Exception(message) {

    class MissingStreamDescription : IosAudioBufferException("The stream description is missing.")
    class InvalidStreamDescription(description: AudioStreamBasicDescription) : IosAudioBufferException("The stream description is invalid. (mBytesPerFrame (=${description.mBytesPerFrame}), must be > 0)")
    class MissingAudioBufferList : IosAudioBufferException("The audio buffer list is missing.")
}