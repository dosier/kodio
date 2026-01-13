package space.kodio.core

/**
 * Sealed hierarchy representing all possible audio-related errors.
 * 
 * Using sealed classes provides exhaustive when-expressions and clear error categorization.
 * 
 * Note: Simple error types use classes (not objects) to ensure proper stack traces
 * are captured when the error is created.
 */
sealed class AudioError(
    message: String,
    cause: Throwable? = null
) : Throwable(message, cause) {

    /**
     * Microphone or audio permission was denied by the user.
     */
    class PermissionDenied : AudioError("Audio permission denied") {
        override fun equals(other: Any?) = other is PermissionDenied
        override fun hashCode() = "PermissionDenied".hashCode()
    }

    /**
     * The requested audio device was not found or is unavailable.
     */
    data class DeviceNotFound(
        val deviceId: String? = null
    ) : AudioError("Audio device not found${deviceId?.let { ": $it" } ?: ""}")

    /**
     * The requested audio format is not supported by the device.
     */
    data class FormatNotSupported(
        val format: AudioFormat? = null
    ) : AudioError("Audio format not supported${format?.let { ": $it" } ?: ""}")

    /**
     * A device-level error occurred during recording or playback.
     */
    data class DeviceError(
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AudioError(errorMessage, errorCause)

    /**
     * Kodio has not been initialized. Call Kodio.initialize() first.
     */
    class NotInitialized : AudioError(
        "Kodio not initialized. Call Kodio.initialize() in your Application class."
    ) {
        override fun equals(other: Any?) = other is NotInitialized
        override fun hashCode() = "NotInitialized".hashCode()
    }

    /**
     * A recording is already in progress.
     */
    class AlreadyRecording : AudioError("A recording is already in progress") {
        override fun equals(other: Any?) = other is AlreadyRecording
        override fun hashCode() = "AlreadyRecording".hashCode()
    }

    /**
     * A playback session is already active.
     */
    class AlreadyPlaying : AudioError("A playback session is already active") {
        override fun equals(other: Any?) = other is AlreadyPlaying
        override fun hashCode() = "AlreadyPlaying".hashCode()
    }

    /**
     * No recording data available.
     */
    class NoRecordingData : AudioError("No recording data available") {
        override fun equals(other: Any?) = other is NoRecordingData
        override fun hashCode() = "NoRecordingData".hashCode()
    }

    /**
     * An unknown or unexpected error occurred.
     */
    data class Unknown(
        val originalCause: Throwable
    ) : AudioError(
        "Unknown audio error: ${originalCause.message ?: originalCause::class.simpleName ?: "Unknown"}",
        originalCause
    )

    companion object {
        /**
         * Wrap any throwable into an appropriate AudioError.
         */
        fun from(throwable: Throwable): AudioError = when (throwable) {
            is AudioError -> throwable
            is space.kodio.core.security.AudioPermissionDeniedException -> PermissionDenied()
            else -> Unknown(throwable)
        }
    }
}
