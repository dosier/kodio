package space.kodio.core

sealed class AppleAudioEngineException(message: String) : Exception(message) {

    class FailedToStart(errorDescription: String) : AppleAudioEngineException(errorDescription)
}