package space.kodio.core

sealed class AVAudioSessionException(message: String) : Exception(message) {

    class FailedToSetCategory(errorDescription: String) : AVAudioSessionException("Failed to set category: $errorDescription")
    class FailedToActivate(errorDescription: String) : AVAudioSessionException("Failed to activate session: $errorDescription")
    class InputNotFound(input: AudioDevice.Input) : AVAudioSessionException("Input $input not found.")
    class FailedToSetPreferredInput(input: AudioDevice.Input, errorDescription: String) : AVAudioSessionException("Failed to set preferred input ($input): $errorDescription")
}