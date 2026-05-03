package space.kodio.transcription.cloud

import io.ktor.client.HttpClient

/**
 * Creates the HttpClient used by [OpenAIWhisperEngine] when the caller does
 * not pass a custom one. JVM prefers OkHttp with HTTP/1.1 only (see `jvmMain` actual).
 */
internal expect fun createDefaultWhisperHttpClient(): HttpClient
