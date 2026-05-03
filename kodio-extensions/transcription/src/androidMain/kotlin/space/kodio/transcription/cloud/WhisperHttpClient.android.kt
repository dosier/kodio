package space.kodio.transcription.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

/**
 * Default Whisper [HttpClient] for JVM/Android targets.
 *
 * Layered transport hardening to keep multipart uploads to the OpenAI Whisper
 * API stable on macOS / JDK 21+, where intermittent
 * `javax.net.ssl.SSLException: Received fatal alert: bad_record_mac` is observed:
 *
 *  1. **HTTP/1.1 only.** OkHttp's default HTTP/2 multiplexes sequential
 *     multipart uploads over a single TLS connection, which races with the
 *     JDK SSL stack on large bodies.
 *  2. **TLS 1.2 only.** Avoids TLS 1.3-specific JDK regressions on Apple
 *     Silicon (notably around ChaCha20-Poly1305). OpenAI's API supports TLS 1.2.
 *  3. **No connection pooling.** Each request gets a fresh TCP+TLS handshake
 *     (~50 ms overhead, negligible vs. Whisper inference time). Eliminates any
 *     pool-corruption / half-closed-keepalive races.
 *  4. **`Connection: close`** on every request, so the server cooperates with
 *     us in not reusing the socket.
 */
internal actual fun createDefaultWhisperHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(Protocol.HTTP_1_1))
                connectionSpecs(
                    listOf(
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_2)
                            .build()
                    )
                )
                connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                addNetworkInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("Connection", "close")
                        .build()
                    chain.proceed(req)
                }
            }
        }
    }
