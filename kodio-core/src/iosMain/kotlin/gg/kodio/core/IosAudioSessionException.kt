package gg.kodio.core

sealed class IosAudioSessionException(message: String) : Exception(message) {

    class FailedToSetCategory(errorDescription: String) : IosAudioSessionException("Failed to set category: $errorDescription")
    class FailedToActivate(errorDescription: String) : IosAudioSessionException("Failed to activate session: $errorDescription")
    class InputNotFound(input: AudioDevice.Input) : IosAudioSessionException("Input $input not found.")
    class FailedToSetPreferredInput(input: AudioDevice.Input, errorDescription: String) : IosAudioSessionException("Failed to set preferred input ($input): $errorDescription")
}