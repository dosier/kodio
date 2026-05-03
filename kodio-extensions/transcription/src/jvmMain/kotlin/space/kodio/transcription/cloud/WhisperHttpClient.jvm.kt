package space.kodio.transcription.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

/**
 * Avoid HTTP/2 for sequential multipart uploads: with OkHttp + JDK TLS on
 * macOS, HTTP/2 multiplexing showed intermittent `bad_record_mac` (harness).
 */
internal actual fun createDefaultWhisperHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(Protocol.HTTP_1_1))
            }
        }
    }
