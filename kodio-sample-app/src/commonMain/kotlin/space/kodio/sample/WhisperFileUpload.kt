package space.kodio.sample

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.http.isSuccess

private const val WHISPER_COST_PER_MINUTE = 0.006
private const val WHISPER_FILE_SIZE_LIMIT_BYTES: Long = 25L * 1024 * 1024
private const val OPENAI_TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions"

private val WHISPER_SUPPORTED_EXTENSIONS = setOf(
    "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "flac", "oga", "ogg",
)

internal sealed class WhisperFileUploadException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class FileTooLarge(val fileSize: Long, val limit: Long) : WhisperFileUploadException(
        "Audio file is $fileSize bytes, exceeds OpenAI Whisper's 25 MB per-request limit ($limit bytes). " +
            "For larger files, decode to PCM and use the streaming OpenAIWhisperEngine.transcribe(AudioFlow) path.",
    )

    class UnsupportedExtension(val extension: String) : WhisperFileUploadException(
        "Audio file extension '$extension' is not supported by OpenAI Whisper. " +
            "Supported: ${WHISPER_SUPPORTED_EXTENSIONS.sorted().joinToString(", ")}.",
    )

    class Api(val statusCode: Int, val responseBody: String) : WhisperFileUploadException(
        "OpenAI Whisper API returned $statusCode: ${responseBody.take(500)}",
    )
}

/**
 * Shared Ktor-based Whisper file transcription used by all non-browser sample-app actuals.
 *
 * Uses Ktor's MultiPartFormDataContent (which streams the body with a real
 * Content-Length and avoids the JDK HttpURLConnection "Error writing to server"
 * failure mode). Reports upload progress via [onUploadProgress].
 *
 * Browsers still cannot call OpenAI directly due to CORS — js/wasm actuals
 * keep their friendly stub.
 */
internal suspend fun transcribeWhisperFileViaKtor(
    fileBytes: ByteArray,
    fileName: String,
    apiKey: String,
    onUploadProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null,
): FileTranscriptionResult {
    if (fileBytes.size.toLong() > WHISPER_FILE_SIZE_LIMIT_BYTES) {
        throw WhisperFileUploadException.FileTooLarge(fileBytes.size.toLong(), WHISPER_FILE_SIZE_LIMIT_BYTES)
    }
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext !in WHISPER_SUPPORTED_EXTENSIONS) {
        throw WhisperFileUploadException.UnsupportedExtension(ext)
    }

    val contentType = guessContentType(fileName)

    println("[TranscriptionShowcase] Uploading $fileName (${fileBytes.size} bytes, $contentType) to OpenAI Whisper…")

    val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60_000L
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 5 * 60_000L
        }
    }
    try {
        val response = client.post(OPENAI_TRANSCRIPTIONS_URL) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            if (onUploadProgress != null) {
                onUpload { bytesSentTotal, contentLength ->
                    onUploadProgress(bytesSentTotal, contentLength ?: -1L)
                }
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = fileBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File
                                        .withParameter(ContentDisposition.Parameters.FileName, fileName)
                                        .toString(),
                                )
                            },
                        )
                        append("model", "whisper-1")
                        append("response_format", "verbose_json")
                    },
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.bodyAsText() }.getOrElse { "<failed to read body>" }
            println("[TranscriptionShowcase] Whisper API error ${response.status.value}: ${errorBody.take(500)}")
            throw WhisperFileUploadException.Api(response.status.value, errorBody)
        }

        val responseText = response.bodyAsText()
        println("[TranscriptionShowcase] API response: ${responseText.take(200)}…")

        val text = extractJsonString(responseText, "text") ?: ""
        val language = extractJsonString(responseText, "language") ?: "unknown"
        val duration = extractJsonNumber(responseText, "duration") ?: 0.0
        val cost = (duration / 60.0) * WHISPER_COST_PER_MINUTE

        return FileTranscriptionResult(
            text = text,
            durationSeconds = duration,
            cost = cost,
            language = language,
        )
    } finally {
        client.close()
    }
}
