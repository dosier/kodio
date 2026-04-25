package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * Transcribes a file using OpenAI Whisper API on WebAssembly.
 *
 * Direct calls to `api.openai.com` are blocked by CORS in the browser. Use
 * `OpenAIWhisperEngine(endpointUrl = "https://your-backend/transcribe", ...)`
 * for live recording transcription, or wire this sample's file upload to your
 * own backend. See `kodio-docs/topics/Transcription-Web.md` for an example
 * proxy implementation.
 */
actual suspend fun transcribeFile(
    file: PlatformFile,
    apiKey: String
): FileTranscriptionResult {
    throw UnsupportedOperationException(
        "Browser file transcription requires a backend proxy because OpenAI's API does not " +
            "send Access-Control-Allow-Origin. Configure OpenAIWhisperEngine with " +
            "`endpointUrl = \"https://your-backend/transcribe\"` for live recording " +
            "transcription, or POST the file to your own proxy from JS. " +
            "See https://github.com/dosier/kodio/issues/16 for details."
    )
}
