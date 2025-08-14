package space.kodio.core

sealed class AppleAudioSessionException(message: String) : Exception(message) {

    class FailedToSetCategory(errorDescription: String) : AppleAudioSessionException("Failed to set category: $errorDescription")
    class FailedToActivate(errorDescription: String) : AppleAudioSessionException("Failed to activate session: $errorDescription")
    class InputNotFound(input: AudioDevice.Input) : AppleAudioSessionException("Input $input not found.")
    class FailedToSetPreferredInput(input: AudioDevice.Input, errorDescription: String) : AppleAudioSessionException("Failed to set preferred input ($input): $errorDescription")
}