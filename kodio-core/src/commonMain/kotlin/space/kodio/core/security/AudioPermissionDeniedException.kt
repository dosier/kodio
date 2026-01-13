package space.kodio.core.security

/**
 * Exception thrown when audio permission is denied.
 */
class AudioPermissionDeniedException : Exception("Audio permission denied") {
    companion object {
        /** Shared message for this exception type */
        const val MESSAGE = "Audio permission denied"
    }
}
