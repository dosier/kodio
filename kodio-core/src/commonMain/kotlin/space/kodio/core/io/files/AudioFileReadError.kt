package space.kodio.core.io.files

/**
 * Defines potential errors that can occur during the file reading process.
 */
sealed class AudioFileReadError(
    message: String? = null,
    cause: Throwable? = null,
) : Throwable(message, cause) {

    /**
     * The data does not represent a valid audio file
     * (e.g. missing RIFF/WAVE header, corrupt structure).
     */
    class InvalidFile(message: String, cause: Throwable? = null) : AudioFileReadError(message = message, cause = cause)

    /**
     * The audio file uses a format or encoding that Kodio does not support
     * (e.g. compressed ADPCM, unsupported bit depth).
     */
    class UnsupportedFormat(message: String, cause: Throwable? = null) : AudioFileReadError(message = message, cause = cause)

    /**
     * Wraps a lower-level I/O exception that occurred while reading from the file system.
     */
    class IO(cause: Exception) : AudioFileReadError(cause = cause)
}
