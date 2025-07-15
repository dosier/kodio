package gg.kodio.core

sealed class IosAudioEngineException(message: String) : Exception(message) {

    class FailedToStart(errorDescription: String) : IosAudioEngineException(errorDescription)
}