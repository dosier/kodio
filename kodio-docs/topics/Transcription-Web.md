[//]: # (title: Web transcription via a backend proxy)

<show-structure for="chapter" depth="2"/>
<primary-label ref="advanced"/>

<tldr>
<p><b>Why this page exists</b>: OpenAI's transcription API does not send <code>Access-Control-Allow-Origin</code>, so calling it directly from a browser is blocked by CORS. Route the request through your own backend instead.</p>
</tldr>

This page explains how to use Kodio's `OpenAIWhisperEngine` from JS / WasmJS
targets without exposing your OpenAI key in the browser.

## The problem {id="problem"}

Browsers refuse the cross-origin request to `api.openai.com` because:

1. OpenAI's API does not include `Access-Control-Allow-Origin` headers in the
   response.
2. Embedding an API key in browser requests would leak it to anyone who opens
   DevTools.

Affects both live recording transcription and file upload transcription on JS
and WasmJS targets. Native targets (JVM, Android, iOS, macOS) are unaffected
because their HTTP stacks are not bound by the browser CORS policy.

## The fix: configure `endpointUrl` {id="endpoint"}

`OpenAIWhisperEngine` (since `0.1.4`) accepts two new constructor parameters:

- `endpointUrl` — the URL the engine will POST audio chunks to. Defaults to
  OpenAI's public endpoint; override it to point at your backend.
- `additionalHeaders` — extra headers appended to every request, e.g. an
  app-level token your backend uses to authenticate the call.

When `endpointUrl` is overridden you can pass an empty `apiKey` and the engine
will skip adding the `Authorization: Bearer …` header — your backend should
hold the OpenAI key.

```kotlin
val engine = OpenAIWhisperEngine(
    apiKey = "",
    endpointUrl = "https://my-app.example.com/api/transcribe",
    additionalHeaders = headersOf("X-App-Token", "browser-secret"),
    chunkDurationSeconds = 3,
)

recorder.audioFlow?.transcribe(engine, TranscriptionConfig(language = "en-US"))
    ?.collect { result -> /* … */ }
```

## Example proxy: Ktor server {id="ktor-proxy"}

A minimal forwarder running on `:8080` that takes the same multipart payload
the engine produces and forwards it to OpenAI:

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) { json() }
        install(CORS) {
            anyHost()                 // tighten this to your front-end origin in production
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-App-Token")
            allowMethod(HttpMethod.Post)
        }
        val openAiKey = System.getenv("OPENAI_API_KEY")
        val client = HttpClient(CIO)

        routing {
            post("/api/transcribe") {
                if (call.request.headers["X-App-Token"] != "browser-secret") {
                    call.respond(HttpStatusCode.Unauthorized); return@post
                }
                val body = call.receiveChannel().toByteArray()
                val response = client.post("https://api.openai.com/v1/audio/transcriptions") {
                    header(HttpHeaders.Authorization, "Bearer $openAiKey")
                    header(HttpHeaders.ContentType, call.request.contentType().toString())
                    setBody(body)
                }
                call.respondBytes(
                    bytes = response.bodyAsBytes(),
                    contentType = ContentType.Application.Json,
                    status = response.status,
                )
            }
        }
    }.start(wait = true)
}
```

The same pattern works on Cloudflare Workers, Vercel/Netlify edge functions,
or any other serverless runtime — anywhere you can hold a secret and forward
a multipart POST.

## File uploads {id="file-uploads"}

`Kodio.transcribe(file, …)` is currently not wired to use `endpointUrl` from
JS/WasmJS in the sample app. The straightforward path is to POST the
`PlatformFile` bytes directly to your proxy from your own JS code, or call
the engine's live recording flow with a one-shot `AudioFlow` produced from the
file. See [GitHub issue #16](https://github.com/dosier/kodio/issues/16) for
status updates.

<seealso style="cards">
    <category ref="advanced">
        <a href="Recording.md" summary="Recording API">Recording</a>
        <a href="File-IO.md" summary="Save and load audio files">Audio File I/O</a>
    </category>
</seealso>
