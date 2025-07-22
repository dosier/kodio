package space.kodio.core.io.files

/**
 * Defines potential errors that can occur during the file writing process.
 */
sealed class AudioFileWriteError {
    /**
     * Indicates that the provided AudioFormat is not supported for the target file format.
     * For example, trying to write a 64-bit audio stream to a standard WAV file.
     */
    data class UnsupportedFormatError(val message: String) : AudioFileWriteError()

    /**
     * Wraps a lower-level I/O exception that occurred during writing to the file system.
     */
    data class IOError(val exception: Exception) : AudioFileWriteError()
}