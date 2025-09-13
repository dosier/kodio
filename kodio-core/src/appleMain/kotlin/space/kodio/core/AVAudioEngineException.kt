package space.kodio.core

sealed class AVAudioEngineException(message: String) : Exception(message) {

    class FailedToStart(errorDescription: String) : AVAudioEngineException(errorDescription)
}