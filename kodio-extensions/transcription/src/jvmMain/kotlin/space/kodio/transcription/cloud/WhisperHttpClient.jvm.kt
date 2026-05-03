package space.kodio.transcription.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Default Whisper [HttpClient] for the JVM target.
 *
 * Layered transport hardening to keep multipart uploads to the OpenAI Whisper
 * API stable across diverse JDK/macOS environments where intermittent
 * `javax.net.ssl.SSLException: Received fatal alert: bad_record_mac` is
 * observed (the JVM SSL stack on JDK 21+ macOS produces corrupted outgoing
 * TLS records under load with multipart bodies):
 *
 *  1. **Conscrypt as the SSL provider.** Replaces JDK's SSLEngine with
 *     Google's BoringSSL-based implementation, eliminating the JDK-specific
 *     MAC-corruption class entirely. (Android already ships Conscrypt.)
 *  2. **HTTP/1.1 only.** Avoids HTTP/2 multiplexing of multipart uploads
 *     over a single TLS connection.
 *  3. **TLS 1.2 only.** Conservative fallback; safe to relax once Conscrypt
 *     adoption is confirmed stable.
 *  4. **No connection pooling.** Fresh TCP+TLS handshake per request.
 *  5. **`Connection: close`** so the server cooperates in not reusing sockets.
 */
internal actual fun createDefaultWhisperHttpClient(): HttpClient {
    val (sslSocketFactory, trustManager) = createConscryptSslSocketFactory()
    return HttpClient(OkHttp) {
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
                sslSocketFactory(sslSocketFactory, trustManager)
                addNetworkInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("Connection", "close")
                        .build()
                    chain.proceed(req)
                }
            }
        }
    }
}

private fun createConscryptSslSocketFactory(): Pair<SSLSocketFactory, X509TrustManager> {
    val provider = Conscrypt.newProvider()
    val sslContext = SSLContext.getInstance("TLS", provider)
    val trustManagerFactory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm()
    )
    trustManagerFactory.init(null as KeyStore?)
    val trustManager = trustManagerFactory.trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()
    sslContext.init(null, arrayOf(trustManager), null)
    return sslContext.socketFactory to trustManager
}
