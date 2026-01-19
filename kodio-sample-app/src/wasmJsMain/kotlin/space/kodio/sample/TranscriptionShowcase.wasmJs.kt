package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * Transcribes a file using OpenAI Whisper API on WebAssembly.
 * Note: File upload from browser has CORS limitations with OpenAI's API.
 * For production use, implement a backend proxy.
 */
actual suspend fun transcribeFile(
    file: PlatformFile,
    apiKey: String
): FileTranscriptionResult {
    // Browser-based file transcription requires a backend proxy due to CORS
    // For now, return an informative error
    throw UnsupportedOperationException(
        "File transcription is not supported in browser. " +
        "Use a backend proxy for the OpenAI API or try the live recording feature instead."
    )
}
