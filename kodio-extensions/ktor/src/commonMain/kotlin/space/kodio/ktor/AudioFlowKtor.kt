package space.kodio.ktor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.send
import kotlinx.coroutines.flow.channelFlow
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.io.decodeAsAudioFormat
import space.kodio.core.io.encodeToByteArray

private val logger = KotlinLogging.logger {}

/**
 * Wire format used by the Kodio Ktor helpers.
 *
 * The first frame on the socket is the binary [AudioFormat] header (encoded
 * via [AudioFormat.encodeToByteArray] and prefixed with [MAGIC]); every
 * subsequent binary frame is one raw PCM chunk in that format.
 */
public object AudioFlowWireFormat {
    /** Short ASCII tag prefixed to the format header for sanity checks. */
    public const val MAGIC: String = "KDIO"
}

/* ====================== WebSocket: client side ====================== */

/**
 * Stream this [AudioFlow] over a WebSocket on the given client.
 *
 * The client must have the `WebSockets` plugin installed. Sends the format
 * header first, then one binary frame per audio chunk, then closes the socket
 * cleanly.
 *
 * @param client A [HttpClient] with the WebSockets plugin installed.
 * @param urlString The full WebSocket URL (e.g. `"ws://host:port/audio"`).
 * @param request Optional block to customise the WebSocket handshake (headers etc.).
 */
public suspend fun AudioFlow.sendOverWebSocket(
    client: HttpClient,
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
) {
    val session: WebSocketSession = client.webSocketSession(urlString, request)
    try {
        sendInto(session)
    } finally {
        session.close()
    }
}

/**
 * Stream this [AudioFlow] into an already-open [WebSocketSession].
 *
 * Useful when the caller already manages the session lifecycle (e.g. a
 * server-side `webSocket { … }` route).
 */
public suspend fun AudioFlow.sendInto(session: WebSocketSession) {
    val header = encodeFormatHeader(format)
    session.send(Frame.Binary(true, header))
    var chunks = 0
    var bytes = 0L
    collect { chunk ->
        session.send(Frame.Binary(true, chunk))
        chunks++
        bytes += chunk.size
    }
    logger.debug { "Sent $chunks chunks ($bytes bytes) over WebSocket" }
}

/**
 * Receive an [AudioFlow] from a remote WebSocket endpoint.
 *
 * The first binary frame is read synchronously to recover the [AudioFormat]
 * header; the resulting [AudioFlow] then emits one element per subsequent
 * binary frame until the peer closes the socket.
 */
public suspend fun HttpClient.receiveAudioFlowFromWebSocket(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
): AudioFlow {
    val session = webSocketSession(urlString, request)
    val headerFrame = session.incoming.receive()
    require(headerFrame is Frame.Binary) {
        "Expected binary AudioFormat header as first frame, got ${headerFrame.frameType}"
    }
    val format = decodeFormatHeader(headerFrame.readBytes())

    val flow = channelFlow {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Binary) {
                    send(frame.readBytes())
                }
            }
        } finally {
            session.close()
        }
    }
    return AudioFlow(format, flow)
}

/* ====================== Server-side WebSocket helper ====================== */

/**
 * Read an [AudioFlow] from an open server-side [WebSocketSession].
 *
 * Mirrors [HttpClient.receiveAudioFlowFromWebSocket] for the server: the
 * first binary frame is consumed as the [AudioFormat] header and subsequent
 * binary frames are surfaced as chunks. The caller is responsible for the
 * session lifecycle.
 */
public suspend fun WebSocketSession.receiveAudioFlow(): AudioFlow {
    val headerFrame = incoming.receive()
    require(headerFrame is Frame.Binary) {
        "Expected binary AudioFormat header as first frame, got ${headerFrame.frameType}"
    }
    val format = decodeFormatHeader(headerFrame.readBytes())
    val flow = channelFlow {
        for (frame in incoming) {
            if (frame is Frame.Binary) {
                send(frame.readBytes())
            }
        }
    }
    return AudioFlow(format, flow)
}

/* ====================== Header helpers ====================== */

/** Encode the [AudioFlowWireFormat.MAGIC]-prefixed binary format header. */
public fun encodeFormatHeader(format: AudioFormat): ByteArray {
    val payload = format.encodeToByteArray()
    val magic = AudioFlowWireFormat.MAGIC.encodeToByteArray()
    return magic + payload
}

/** Decode the [AudioFlowWireFormat.MAGIC]-prefixed binary format header. */
public fun decodeFormatHeader(bytes: ByteArray): AudioFormat {
    val magic = AudioFlowWireFormat.MAGIC.encodeToByteArray()
    require(bytes.size >= magic.size && bytes.copyOf(magic.size).contentEquals(magic)) {
        "AudioFlow header is missing the '${AudioFlowWireFormat.MAGIC}' magic prefix"
    }
    return bytes.copyOfRange(magic.size, bytes.size).decodeAsAudioFormat()
}
