package space.kodio.transcription.cloud

import io.ktor.client.HttpClient

/**
 * Creates the HttpClient used by [OpenAIWhisperEngine] when the caller does
 * not pass a custom one.
 *
 * Each platform may apply transport-specific tuning here. In particular, the
 * JVM and Android actuals force HTTP/1.1 in OkHttp because HTTP/2 multiplexing
 * over a single JDK TLS connection (especially with multipart bodies > ~1 MB)
 * can produce `SSLException: Received fatal alert: bad_record_mac` due to a
 * long-standing JDK + OkHttp interaction (see Square OkHttp issue #4504,
 * JDK-8208526).
 */
internal expect fun createDefaultWhisperHttpClient(): HttpClient
