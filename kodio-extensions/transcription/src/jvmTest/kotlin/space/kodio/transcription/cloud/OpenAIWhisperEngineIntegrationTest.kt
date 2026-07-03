package space.kodio.transcription.cloud

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.io.files.AudioFileReader
import space.kodio.transcription.TranscriptionConfig
import space.kodio.transcription.TranscriptionResult
import space.kodio.transcription.transcribe
import java.io.File
import java.util.Properties
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * End-to-end regression test against the real OpenAI Whisper API.
 *
 * **Cost & safety:** This test calls OpenAI for real (~$0.005 per run). To
 * keep `./gradlew jvmTest` free by default, the test auto-skips unless
 * `KODIO_RUN_LIVE_INTEGRATION=true` is set in the environment. To run:
 *
 * ```
 * KODIO_RUN_LIVE_INTEGRATION=true \
 *   ./gradlew :kodio-extensions:transcription:jvmTest \
 *     --tests space.kodio.transcription.cloud.OpenAIWhisperEngineIntegrationTest
 * ```
 *
 * Additionally requires:
 *  - `openai.api.key=sk-...` in `local.properties` at the repo root, AND
 *  - `kodio.test.wav.path=/path/to/test.wav` in `local.properties`.
 *
 * **What it covers:** drives the same code path the sample app uses for
 * file transcription (file bytes → [AudioFileReader.read] → engine
 * `transcribe`) against the engine's actual JVM default HttpClient. If
 * this test starts failing with `bad_record_mac` / `Broken pipe` / etc.,
 * the JVM HttpClient default in `WhisperHttpClient.jvm.kt` regressed.
 *
 * The HttpClient configuration was empirically chosen; see commit history
 * around `OkHttp + HTTP/1.1 only` for the comparator data.
 */
class OpenAIWhisperEngineIntegrationTest {

    companion object {
        private const val MAX_CHUNKS = 5
        private const val CHUNK_SECONDS = 10
        private const val OPT_IN_ENV = "KODIO_RUN_LIVE_INTEGRATION"
    }

    private data class TestConfig(val apiKey: String, val wavPath: String)

    private fun testConfigOrSkip(): TestConfig {
        Assume.assumeTrue(
            "Live OpenAI integration harness: set $OPT_IN_ENV=true to run.",
            System.getenv(OPT_IN_ENV) == "true",
        )
        val props = Properties()
        val candidates = listOf(
            File("local.properties"),
            File("../../local.properties"),
            File(System.getProperty("user.dir")).resolve("local.properties"),
        )
        val propsFile = candidates.firstOrNull { it.exists() }
        Assume.assumeTrue(
            "local.properties not found from cwd=${System.getProperty("user.dir")}",
            propsFile != null,
        )
        propsFile!!.inputStream().use { props.load(it) }
        val apiKey = props.getProperty("openai.api.key")?.trim().orEmpty()
        Assume.assumeTrue("openai.api.key missing in local.properties", apiKey.isNotBlank())
        val wavPath = props.getProperty("kodio.test.wav.path")?.trim().orEmpty()
        Assume.assumeTrue(
            "kodio.test.wav.path missing or blank in local.properties; set it to a WAV file on disk",
            wavPath.isNotBlank(),
        )
        Assume.assumeTrue("Test WAV missing at $wavPath", File(wavPath).exists())
        return TestConfig(apiKey, wavPath)
    }

    private fun harnessPcmFromWav(wavPath: String): Pair<AudioFormat, ByteArray> {
        val wavFile = File(wavPath)
        val recording = AudioFileReader.read(wavFile.readBytes(), wavFile.name)
        val format = recording.format
        val bytesPerSecond = format.sampleRate * format.bytesPerFrame
        val targetPcmBytes = bytesPerSecond * CHUNK_SECONDS * MAX_CHUNKS
        val pcm = recording.toByteArray()
        val take = min(targetPcmBytes, pcm.size)
        return format to (if (take == pcm.size) pcm else pcm.copyOfRange(0, take))
    }

    /**
     * Drives the engine end-to-end against the real OpenAI Whisper API using
     * the engine's default JVM [io.ktor.client.HttpClient], i.e. exactly
     * what the sample app and library consumers get out of the box. Asserts
     * that all chunks transcribe cleanly with no transport errors.
     */
    @Test
    fun liveTranscription_succeedsOnDefaultJvmClient() {
        val config = testConfigOrSkip()
        val engine = OpenAIWhisperEngine(
            apiKey = config.apiKey,
            chunkDurationSeconds = CHUNK_SECONDS,
            // Intentionally NOT passing httpClient; we want to validate
            // the engine's default JVM client (createDefaultWhisperHttpClient).
        )
        try {
            val (format, pcm) = harnessPcmFromWav(config.wavPath)
            val expectedChunks =
                if (pcm.isEmpty()) 0
                else (pcm.size + (format.sampleRate * format.bytesPerFrame * CHUNK_SECONDS) - 1) /
                    (format.sampleRate * format.bytesPerFrame * CHUNK_SECONDS)

            println("[IT] expectedChunks=$expectedChunks pcmBytes=${pcm.size}")

            val audioFlow = AudioFlow(format, flow { emit(pcm) })

            val results = runBlocking {
                audioFlow.transcribe(engine, TranscriptionConfig.Default).toList()
            }

            val finals = results.filterIsInstance<TranscriptionResult.Final>()
            val errors = results.filterIsInstance<TranscriptionResult.Error>()
            println(
                "[IT] results=${results.size} finals=${finals.size} errors=${errors.size} " +
                    "(expected Finals=$expectedChunks, Errors=0)",
            )
            errors.forEachIndexed { i, e ->
                println("[IT] error[$i]: ${e.message.take(200)}")
            }

            assertTrue(
                errors.isEmpty(),
                "Default JVM HttpClient regressed: expected 0 transport errors but got " +
                    "${errors.size}. First: ${errors.firstOrNull()?.message?.take(200)}",
            )
            assertTrue(
                finals.size == expectedChunks,
                "Expected $expectedChunks Final transcripts but got ${finals.size}. " +
                    "(errors=${errors.size}, totalResults=${results.size})",
            )
        } finally {
            engine.release()
        }
    }
}
