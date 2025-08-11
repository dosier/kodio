package space.kodio.core.io.files

/**
 * Defines potential errors that can occur during the file writing process.
 */
sealed class AudioFileWriteError(
    message: String? = null,
    cause: Throwable? = null,
) : Throwable(message, cause) {

    /**
     * Indicates that the provided AudioFormat is not supported for the target file format.
     * For example, trying to write a 64-bit audio stream to a standard WAV file.
     */
    class UnsupportedFormat(message: String) : AudioFileWriteError(message = message)

    /**
     * Wraps a lower-level I/O exception that occurred during writing to the file system.
     */
    class IO(cause: Exception) : AudioFileWriteError(cause = cause)
}