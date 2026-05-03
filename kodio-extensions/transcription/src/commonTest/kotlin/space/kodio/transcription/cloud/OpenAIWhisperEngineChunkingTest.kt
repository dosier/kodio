package space.kodio.transcription.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import space.kodio.core.*
import space.kodio.transcription.TranscriptionResult
import space.kodio.transcription.transcribe

class OpenAIWhisperEngineChunkingTest {

    private val pcm16Format = AudioFormat(
        sampleRate = 16_000,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(
            bitDepth = IntBitDepth.Sixteen,
            endianness = Endianness.Little,
            layout = SampleLayout.Interleaved,
            signed = true,
            packed = true,
        )
    )

    private fun mockClient(onCall: (Int) -> Unit): HttpClient {
        var callCount = 0
        return HttpClient(MockEngine { request ->
            val n = ++callCount
            onCall(n)
            respond(
                content = ByteReadChannel(
                    """{"text":"chunk $n text","duration":2.0,"language":"en"}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
    }

    @Test
    fun `emits one Final per full chunk`() = runTest {
        val chunkSeconds = 2
        // bytes per second = 16000 * 2 (mono, 16-bit interleaved) = 32_000
        // chunk size = 32_000 * 2 = 64_000 bytes
        // generate 3.5 chunks → expect 4 Finals (3 full + 1 tail)
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val chunkBytes = bytesPerSecond * chunkSeconds
        val total = (chunkBytes * 3.5).toInt()

        val data = ByteArray(total) { (it and 0xFF).toByte() }
        // Emit in small windows to exercise buffering
        val flow = flow {
            var offset = 0
            val window = 4096
            while (offset < data.size) {
                val end = minOf(offset + window, data.size)
                emit(data.copyOfRange(offset, end))
                offset = end
            }
        }
        val audioFlow = AudioFlow(pcm16Format, flow)

        var calls = 0
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = mockClient { calls = it }
        )

        val results = audioFlow.transcribe(engine).toList()
        engine.release()

        val finals = results.filterIsInstance<TranscriptionResult.Final>()
        assertEquals(4, finals.size, "Expected 3 full chunks + 1 tail = 4 Finals; got: $results")
        assertEquals(4, calls, "MockEngine should receive one POST per chunk")
        finals.forEachIndexed { i, f ->
            assertTrue(f.text.contains("chunk"), "Final[$i] text=${f.text}")
        }
    }

    @Test
    fun `single large emission is chunked into multiple uploads`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val chunkBytes = bytesPerSecond * chunkSeconds
        val total = chunkBytes * 4

        val data = ByteArray(total) { (it and 0xFF).toByte() }
        val flow = flow { emit(data) }
        val audioFlow = AudioFlow(pcm16Format, flow)

        var calls = 0
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = mockClient { calls = it }
        )

        val results = audioFlow.transcribe(engine).toList()
        engine.release()

        val finals = results.filterIsInstance<TranscriptionResult.Final>()
        assertEquals(4, finals.size, "Engine must drain a large single emission into N full chunks; got $finals")
        assertEquals(4, calls, "Should POST exactly 4 times for 4 full chunks (no tail)")
    }

    @Test
    fun `tail-only audio shorter than one chunk still gets transcribed`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val tailBytes = bytesPerSecond / 2  // 0.5 s of audio

        val data = ByteArray(tailBytes) { 0 }
        val flow = flow { emit(data) }
        val audioFlow = AudioFlow(pcm16Format, flow)

        var calls = 0
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = mockClient { calls = it }
        )
        val results = audioFlow.transcribe(engine).toList()
        engine.release()

        assertEquals(1, calls, "Should still POST a single chunk for tail-only audio")
        assertEquals(1, results.filterIsInstance<TranscriptionResult.Final>().size)
    }

    @Test
    fun `engine reports unavailable when no api key and default endpoint`() = runTest {
        val engine = OpenAIWhisperEngine(apiKey = "")
        assertFalse(engine.isAvailable)
        engine.release()
    }

    @Test
    fun `unrecoverable error stops the stream and prevents further uploads`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val chunkBytes = bytesPerSecond * chunkSeconds
        val total = chunkBytes * 4

        val data = ByteArray(total) { (it and 0xFF).toByte() }
        val flow = flow { emit(data) }
        val audioFlow = AudioFlow(pcm16Format, flow)

        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            respond(
                content = ByteReadChannel(
                    """{"error":{"message":"Incorrect API key","type":"invalid_request_error","code":"invalid_api_key"},"status":401}"""
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = client
        )

        val results = audioFlow.transcribe(engine).toList()
        engine.release()

        assertEquals(1, calls, "Engine must stop after the first unrecoverable error; got $calls POSTs")
        val errors = results.filterIsInstance<TranscriptionResult.Error>()
        assertEquals(1, errors.size, "Expected exactly one Error result; got: $results")
        assertFalse(errors[0].isRecoverable, "Error must be flagged unrecoverable")
        assertEquals("401", errors[0].code, "Error.code should carry the HTTP status")
        assertTrue(results.filterIsInstance<TranscriptionResult.Final>().isEmpty(), "No Final results expected on auth failure")
    }

    @Test
    fun `recoverable 5xx is retried then surfaces as success on second attempt`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val data = ByteArray(bytesPerSecond * chunkSeconds) { 0 }

        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            if (calls == 1) {
                respond(
                    content = ByteReadChannel("""{"error":"upstream"}"""),
                    status = HttpStatusCode.BadGateway,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """{"text":"recovered","duration":2.0,"language":"en"}"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = client
        )

        val results = AudioFlow(pcm16Format, flow { emit(data) })
            .transcribe(engine)
            .toList()
        engine.release()

        assertEquals(2, calls, "Should retry exactly once (1 initial + 1 retry)")
        val finals = results.filterIsInstance<TranscriptionResult.Final>()
        assertEquals(1, finals.size, "Retry should surface as a single Final, not Error+Final")
        assertEquals("recovered", finals[0].text)
        val errors = results.filterIsInstance<TranscriptionResult.Error>()
        assertTrue(errors.isEmpty(), "No Error should be emitted when retry succeeds; got $errors")
    }

    @Test
    fun `recoverable 5xx eventually surfaces as Error after exhausting retries`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val data = ByteArray(bytesPerSecond * chunkSeconds) { 0 }

        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            respond(
                content = ByteReadChannel("""{"error":"upstream still bad"}"""),
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = client
        )

        val results = AudioFlow(pcm16Format, flow { emit(data) })
            .transcribe(engine)
            .toList()
        engine.release()

        assertEquals(5, calls, "Should attempt the configured maximum (5) before giving up")
        val errors = results.filterIsInstance<TranscriptionResult.Error>()
        assertEquals(1, errors.size, "Only the final exhausted Error should be emitted")
        assertTrue(errors[0].isRecoverable, "5xx remains classified as recoverable even after retry exhaustion")
        assertEquals("502", errors[0].code)
    }

    @Test
    fun `unrecoverable 4xx is not retried`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val data = ByteArray(bytesPerSecond * chunkSeconds) { 0 }

        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            respond(
                content = ByteReadChannel("""{"error":"bad request"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = client
        )

        AudioFlow(pcm16Format, flow { emit(data) })
            .transcribe(engine)
            .toList()
        engine.release()

        assertEquals(1, calls, "4xx must not be retried; saw $calls POSTs")
    }

    @Test
    fun `recoverable 5xx is retried then surfaces as success on fourth attempt`() = runTest {
        val chunkSeconds = 2
        val bytesPerSecond = pcm16Format.sampleRate * pcm16Format.bytesPerFrame
        val data = ByteArray(bytesPerSecond * chunkSeconds) { 0 }

        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            if (calls < 4) {
                respond(
                    content = ByteReadChannel("""{"error":"upstream"}"""),
                    status = HttpStatusCode.BadGateway,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """{"text":"recovered on 4th","duration":2.0,"language":"en"}"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })
        val engine = OpenAIWhisperEngine(
            apiKey = "test-key",
            chunkDurationSeconds = chunkSeconds,
            httpClient = client
        )

        val results = AudioFlow(pcm16Format, flow { emit(data) })
            .transcribe(engine)
            .toList()
        engine.release()

        assertEquals(4, calls, "Should succeed on the 4th POST (after 3 failed 5xx responses)")
        val finals = results.filterIsInstance<TranscriptionResult.Final>()
        assertEquals(1, finals.size, "Retry should surface as a single Final; got $results")
        assertEquals("recovered on 4th", finals[0].text)
        assertTrue(results.filterIsInstance<TranscriptionResult.Error>().isEmpty())
    }
}
