package space.kodio.transcription.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

internal actual fun createDefaultWhisperHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                // Force HTTP/1.1 to avoid intermittent `SSLException: Received
                // fatal alert: bad_record_mac` from JDK SSL when OkHttp
                // multiplexes large multipart uploads over HTTP/2.
                protocols(listOf(Protocol.HTTP_1_1))
            }
        }
    }
