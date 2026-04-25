[//]: # (title: Streaming AudioFlow over Ktor)

<show-structure for="chapter" depth="2"/>
<primary-label ref="advanced"/>

<tldr>
<p><b>Push or pull live audio over the network</b> using the
<code>kodio-extensions:ktor</code> module. Works on JVM, Android, iOS, macOS,
JS and WasmJS — anywhere Ktor's client runs.</p>
</tldr>

The Ktor extension adds tiny adapters between Kodio's `AudioFlow` and Ktor's
WebSocket / Server-Sent-Events plumbing so you can stream live mic capture to
a backend (or pull a remote stream into a `Player`) without reinventing the
framing.

## Wire format {id="wire-format"}

The protocol is intentionally minimal:

1. **Format header** — the [`AudioFormat`](Audio-Format.md) is encoded with
   `AudioFormat.encodeToByteArray()`, prefixed with the four ASCII bytes
   `KDIO`, and sent as the first binary frame.
2. **Chunks** — every subsequent binary frame is one raw PCM byte chunk in
   the declared format.

The constants live in `AudioFlowWireFormat`.

## Send a recorder over WebSocket {id="send-ws"}

Stream a live recording from a mobile/desktop client to a server endpoint:

```kotlin
val client = HttpClient(CIO) { install(WebSockets) }
val recorder = Kodio.recorder(quality = AudioQuality.Voice)

recorder.start()
recorder.audioFlow!!.sendOverWebSocket(client, "wss://my-app.example.com/api/audio")
recorder.stop()
client.close()
```

`AudioFlow.sendOverWebSocket` opens the socket, sends the format header,
forwards every chunk as a binary frame, and closes cleanly.

## Receive an AudioFlow on the server {id="receive-server"}

A Ktor server can read the same protocol with one call:

```kotlin
embeddedServer(CIO, port = 8080) {
    install(WebSockets)
    routing {
        webSocket("/api/audio") {
            val flow = receiveAudioFlow()
            println("Receiving ${flow.format}")
            flow.collect { chunk -> /* persist, transcribe, … */ }
        }
    }
}.start(wait = true)
```

Or call `WebSocketSession.receiveAudioFlow()` from any custom handler that
already manages the session lifecycle.

## Pull an AudioFlow from a server {id="pull-client"}

Symmetrically, fetch a server-pushed `AudioFlow` into a Kodio `Player`:

```kotlin
val client = HttpClient(CIO) { install(WebSockets) }
val flow = client.receiveAudioFlowFromWebSocket("wss://my-app.example.com/api/audio/play")

Kodio.play { player ->
    player.load(AudioRecording.fromAudioFlow(flow))
    player.start()
}
```

## SSE fallback {id="sse"}

The current release ships WebSocket support only. SSE-based delivery (for
environments where WebSockets are blocked) is planned as a follow-up and will
re-use the same `KDIO`-prefixed header — track [GitHub issue #6](https://github.com/dosier/kodio/issues/6)
for status.

## Maven coordinates {id="install"}

```kotlin
dependencies {
    implementation("space.kodio.extensions:ktor:0.1.4")
}
```

This module declares `kodio-core`, `ktor-client-core`, and
`ktor-client-websockets` as `api` dependencies — bring your own engine
(`ktor-client-cio`, `ktor-client-darwin`, `ktor-client-js`, …).
