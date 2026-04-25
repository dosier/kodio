package space.kodio.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.AudioQuality
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip test for the WebSocket extensions:
 *
 *   client.AudioFlow.sendOverWebSocket
 *      └─→ server route reads AudioFlow via WebSocketSession.receiveAudioFlow
 *
 * Plus a symmetric flow using receiveAudioFlowFromWebSocket on the client.
 *
 * Uses a real (in-process) Ktor server on a random port to exercise CIO-based
 * binary framing rather than mocking, since binary WebSocket framing is
 * exactly what the extension is wrapping.
 */
class AudioFlowKtorWebSocketTest {

    private lateinit var server: io.ktor.server.engine.EmbeddedServer<*, *>
    private var port: Int = 0
    private val received = mutableListOf<ByteArray>()
    @Volatile private var receivedFormat: AudioFormat? = null

    @BeforeTest
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0) {
            install(ServerWebSockets)
            routing {
                webSocket("/audio") {
                    val flow = receiveAudioFlow()
                    receivedFormat = flow.format
                    flow.collect { chunk -> received.add(chunk) }
                }

                webSocket("/echo") {
                    // Echo a fixed AudioFlow back to the client so we can
                    // exercise the receive side too.
                    val format = AudioQuality.Voice.format
                    val chunks = listOf(
                        byteArrayOf(1, 2, 3, 4),
                        byteArrayOf(5, 6, 7, 8),
                        byteArrayOf(9, 10),
                    )
                    AudioFlow(format, flowOf(*chunks.toTypedArray())).sendInto(this)
                }
            }
        }
        server.start(wait = false)
        port = runBlocking {
            server.engine.resolvedConnectors().first().port
        }
    }

    @AfterTest
    fun stopServer() {
        server.stop(0, 0)
    }

    @Test
    fun `client can stream an AudioFlow into a server route`() = runBlocking {
        val format = AudioQuality.Voice.format
        val chunks = listOf(
            byteArrayOf(10, 20, 30, 40),
            byteArrayOf(50, 60, 70, 80, 90),
            byteArrayOf(100),
        )
        val flow = AudioFlow(format, flowOf(*chunks.toTypedArray()))

        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            flow.sendOverWebSocket(client, "ws://127.0.0.1:$port/audio")
        } finally {
            client.close()
        }

        // Allow the server-side suspend collect to complete after the client
        // closes the socket. CIO drains synchronously here, but a small spin
        // keeps the test stable in CI.
        var attempts = 0
        while (received.size < chunks.size && attempts++ < 50) {
            Thread.sleep(20)
        }

        assertEquals(format, receivedFormat)
        assertEquals(chunks.size, received.size)
        for (i in chunks.indices) {
            assertTrue(
                chunks[i].contentEquals(received[i]),
                "Chunk #$i differs: expected ${chunks[i].toList()} but got ${received[i].toList()}",
            )
        }
    }

    @Test
    fun `client can read an AudioFlow from a server route`() = runBlocking {
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            val flow = client.receiveAudioFlowFromWebSocket("ws://127.0.0.1:$port/echo")
            assertEquals(AudioQuality.Voice.format, flow.format)

            val chunks = flow.toList()
            assertEquals(3, chunks.size)
            assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(chunks[0]))
            assertTrue(byteArrayOf(5, 6, 7, 8).contentEquals(chunks[1]))
            assertTrue(byteArrayOf(9, 10).contentEquals(chunks[2]))
        } finally {
            client.close()
        }
    }
}
