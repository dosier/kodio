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
}
