package space.kodio.transcription.cloud

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.kodio.core.*
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToSink
import space.kodio.core.logging.kodioLogger
import space.kodio.transcription.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = kodioLogger("OpenAIWhisperEngine")

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * OpenAI Whisper API transcription engine.
 *
 * Note: This is NOT true real-time streaming. OpenAI's Whisper API processes
 * complete audio files, so this engine buffers audio and sends it in chunks
 * rather than doing true low-latency streaming.
 *
 * This engine is best suited for:
 * - Post-recording transcription
 * - High accuracy requirements
 * - Short audio clips
 *
 * ## Example
 * ```kotlin
 * val engine = OpenAIWhisperEngine(apiKey = "your-openai-api-key")
 *
 * // Best used with complete recordings
 * val recording = recorder.getRecording()
 * recording?.asAudioFlow()?.transcribe(engine)?.collect { result ->
 *     println("Transcription: ${result}")
 * }
 * ```
 *
 * ## Browsers (JS / WasmJS)
 *
 * OpenAI's API does not send `Access-Control-Allow-Origin`, so direct calls
 * from a browser are blocked by CORS. Stand up a thin backend that forwards
 * the multipart upload to OpenAI and point [endpointUrl] at it. Your backend
 * keeps the API key; pass any auth headers it requires via
 * [additionalHeaders] (and leave [apiKey] blank if your backend doesn't
 * proxy the bearer token).
 *
 * ```kotlin
 * val engine = OpenAIWhisperEngine(
 *     apiKey = "",
 *     endpointUrl = "https://my-app.example.com/api/transcribe",
 *     additionalHeaders = headersOf("X-App-Token", "client-secret"),
 * )
 * ```
 *
 * @param apiKey Your OpenAI API key. Leave empty when proxying through your
 *   own backend (see [endpointUrl]).
 * @param model The Whisper model to use (default: `"whisper-1"`).
 * @param httpClient HTTP client for API calls (default: platform [createDefaultWhisperHttpClient]).
 * @param chunkDurationSeconds Duration in seconds for each chunk (for
 *   streaming mode).
 * @param endpointUrl The URL to POST audio chunks to. Defaults to OpenAI's
 *   public endpoint; override to point at your own proxy when running in a
 *   browser (avoids CORS issues, see [GitHub issue #16](https://github.com/dosier/kodio/issues/16)).
 * @param additionalHeaders Extra headers appended to every request (useful
 *   for proxy authentication tokens, tracing, etc.).
 */
class OpenAIWhisperEngine(
    private val apiKey: String,
    private val model: String = "whisper-1",
    private val httpClient: HttpClient = createDefaultWhisperHttpClient(),
    private val chunkDurationSeconds: Int = 10,
    private val endpointUrl: String = OPENAI_TRANSCRIPTIONS_URL,
    private val additionalHeaders: Headers = Headers.Empty,
) : TranscriptionEngine {

    companion object {
        const val OPENAI_TRANSCRIPTIONS_URL: String = "https://api.openai.com/v1/audio/transcriptions"
    }

    private val MAX_TRANSCRIBE_ATTEMPTS = 5
    private val RETRY_BACKOFF_MS = listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)
    
    override val provider = TranscriptionProvider.OPENAI_WHISPER

    /**
     * The engine is available when either an [apiKey] is configured or the
     * caller has pointed [endpointUrl] at a custom proxy that supplies its own
     * authentication.
     */
    override val isAvailable: Boolean
        get() = apiKey.isNotBlank() || endpointUrl != OPENAI_TRANSCRIPTIONS_URL
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flow {
        logger.debug { "=== OpenAI Whisper Transcription Starting ===" }
        logger.debug { "API key present: ${apiKey.isNotBlank()}" }
        logger.debug { "Model: $model, Chunk duration: ${chunkDurationSeconds}s" }
        
        if (!isAvailable) {
            logger.error { "OpenAI Whisper engine is not available: no API key and no custom endpointUrl set" }
            emit(TranscriptionResult.Error(
                message = "OpenAI Whisper engine not configured: provide an apiKey, or a custom endpointUrl pointing at a backend proxy",
                code = "NO_API_KEY",
                isRecoverable = false
            ))
            return@flow
        }
        
        val format = audioFlow.format
        val bytesPerSecond = format.sampleRate * format.bytesPerFrame
        val chunkSize = bytesPerSecond * chunkDurationSeconds
        
        logger.debug { "Audio format: sampleRate=${format.sampleRate}, channels=${format.channels}, encoding=${format.encoding}" }
        logger.debug { "Bytes per second: $bytesPerSecond, Chunk size target: $chunkSize bytes (${chunkDurationSeconds}s)" }
        
        var buffer = Buffer()
        var chunkIndex = 0
        var totalBytesReceived = 0L
        var audioChunkCount = 0
        var totalSecondsTranscribed = 0.0
        var totalCost = 0.0
        var abortUnrecoverable = false
        
        logger.debug { "Starting to collect audio flow..." }
        
        audioFlow.collect { audioChunk ->
            if (abortUnrecoverable) return@collect
            audioChunkCount++
            totalBytesReceived += audioChunk.size
            buffer.write(audioChunk)
            
            if (audioChunkCount <= 5 || audioChunkCount % 50 == 0) {
                logger.debug { "Audio chunk #$audioChunkCount: ${audioChunk.size} bytes, buffer: ${buffer.size}/$chunkSize bytes, total received: $totalBytesReceived" }
            }
            
            // Drain as many full chunks as the buffer holds. The source flow may emit
            // arbitrarily large ByteArrays (e.g. AudioRecording.asAudioFlow() emits the
            // entire decoded PCM as one chunk for file inputs); a single emission must
            // be fully chunked here, not just the first chunkSize bytes.
            while (buffer.size >= chunkSize && !abortUnrecoverable) {
                logger.debug { ">>> Buffer full! Preparing chunk $chunkIndex for transcription..." }
                val chunkData = buffer.readByteArray(chunkSize.toInt())
                
                // Create WAV data for this chunk
                val wavData = createWavData(format, chunkData)
                logger.debug { "Created WAV data: ${wavData.size} bytes (PCM: ${chunkData.size} bytes)" }
                
                val (result, usage) = transcribeChunk(wavData, config, chunkIndex)
                if (usage != null) {
                    totalSecondsTranscribed += usage.seconds
                    totalCost += usage.cost
                    logger.debug { "Chunk $chunkIndex: ${usage.seconds}s transcribed, cost: \$${formatCost(usage.cost)}" }
                }
                logger.debug { "Chunk $chunkIndex result: $result" }
                if (result != null) {
                    emit(result)
                    if (result is TranscriptionResult.Error && !result.isRecoverable) {
                        logger.warn {
                            "Chunk $chunkIndex returned an unrecoverable error (code=${result.code}); " +
                                "aborting transcription stream."
                        }
                        abortUnrecoverable = true
                        break
                    }
                }
                chunkIndex++
            }
        }
        
        if (abortUnrecoverable) {
            return@flow
        }
        
        logger.debug { "Audio flow collection ended. Total chunks received: $audioChunkCount, Total bytes: $totalBytesReceived" }
        logger.debug { "Remaining buffer size: ${buffer.size} bytes" }
        
        // Process remaining audio
        if (buffer.size > 0) {
            logger.debug { "Processing remaining ${buffer.size} bytes as final chunk..." }
            val remainingData = buffer.readByteArray()
            val wavData = createWavData(format, remainingData)
            logger.debug { "Created final WAV data: ${wavData.size} bytes" }
            val (result, usage) = transcribeChunk(wavData, config, chunkIndex)
            if (usage != null) {
                totalSecondsTranscribed += usage.seconds
                totalCost += usage.cost
                logger.debug { "Final chunk: ${usage.seconds}s transcribed, cost: \$${formatCost(usage.cost)}" }
            }
            logger.debug { "Final chunk result: $result" }
            if (result != null) {
                emit(result)
                if (result is TranscriptionResult.Error && !result.isRecoverable) {
                    logger.warn {
                        "Final chunk returned an unrecoverable error (code=${result.code}); " +
                            "aborting transcription stream."
                    }
                    return@flow
                }
            }
        } else {
            logger.warn { "No remaining audio data to process" }
        }
        
        logger.debug { "=== OpenAI Whisper Transcription Complete ===" }
        logger.debug { "Usage summary:" }
        logger.debug { "   Total audio transcribed: ${totalSecondsTranscribed}s (${formatMinutes(totalSecondsTranscribed)} minutes)" }
        logger.debug { "   Total cost: \$${formatCost(totalCost)}" }
        logger.debug { "   Chunks processed: $chunkIndex" }
    }
    
    /** Format cost to 4 decimal places (KMP compatible) */
    private fun formatCost(value: Double): String {
        val scaled = (value * 10000).toLong()
        val intPart = scaled / 10000
        val decPart = (scaled % 10000).toString().padStart(4, '0')
        return "$intPart.$decPart"
    }
    
    /** Format minutes to 2 decimal places (KMP compatible) */
    private fun formatMinutes(seconds: Double): String {
        val minutes = seconds / 60.0
        val scaled = (minutes * 100).toLong()
        val intPart = scaled / 100
        val decPart = (scaled % 100).toString().padStart(2, '0')
        return "$intPart.$decPart"
    }
    
    private suspend fun createWavData(format: AudioFormat, pcmData: ByteArray): ByteArray {
        val sink = Buffer()
        AudioFlow(format, flowOf(pcmData)).writeToSink(AudioFileFormat.Wav, sink)
        return sink.readByteArray()
    }
    
    /**
     * Usage information from a transcription chunk.
     */
    private data class ChunkUsage(val seconds: Double, val cost: Double)
    
    private suspend fun transcribeChunk(
        wavData: ByteArray,
        config: TranscriptionConfig,
        chunkIndex: Int
    ): Pair<TranscriptionResult?, ChunkUsage?> {
        var lastResult: Pair<TranscriptionResult?, ChunkUsage?> = Pair(null, null)
        repeat(MAX_TRANSCRIBE_ATTEMPTS) { attempt ->
            val (result, usage) = transcribeChunkOnce(wavData, config, chunkIndex, attempt)

            if (result == null && usage == null) {
                return Pair(null, null)
            }
            if (result == null) {
                return Pair(null, usage)
            }

            if (result is TranscriptionResult.Final) {
                return Pair(result, usage)
            }

            val err = result as TranscriptionResult.Error
            val isLastAttempt = attempt == MAX_TRANSCRIBE_ATTEMPTS - 1
            if (!err.isRecoverable || isLastAttempt) {
                return Pair(err, usage)
            }

            val baseDelay = RETRY_BACKOFF_MS.getOrElse(attempt) { RETRY_BACKOFF_MS.last() }
            val jitterRange = baseDelay / 4
            val jitter = kotlin.random.Random.nextLong(-jitterRange, jitterRange + 1)
            val delayMs = (baseDelay + jitter).coerceAtLeast(1L)
            logger.warn {
                "Chunk $chunkIndex attempt ${attempt + 1}/$MAX_TRANSCRIBE_ATTEMPTS failed " +
                    "(recoverable: ${err.message.take(120)}). Retrying in ${delayMs}ms..."
            }
            delay(delayMs)
            lastResult = Pair(err, usage)
        }
        return lastResult
    }

    private suspend fun transcribeChunkOnce(
        wavData: ByteArray,
        config: TranscriptionConfig,
        chunkIndex: Int,
        attempt: Int
    ): Pair<TranscriptionResult?, ChunkUsage?> {
        return try {
            logger.debug {
                if (attempt > 0) {
                    ">>> Sending chunk $chunkIndex (${wavData.size} bytes) to OpenAI Whisper API... " +
                        "(attempt ${attempt + 1}/$MAX_TRANSCRIBE_ATTEMPTS)"
                } else {
                    ">>> Sending chunk $chunkIndex (${wavData.size} bytes) to OpenAI Whisper API..."
                }
            }
            logger.debug { "Config: language=${config.language}, model=$model" }

            val timeMark = TimeSource.Monotonic.markNow()

            val response = httpClient.post(endpointUrl) {
                headers {
                    if (apiKey.isNotBlank()) {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    additionalHeaders.forEach { name, values ->
                        values.forEach { value -> append(name, value) }
                    }
                }
                setBody(MultiPartFormDataContent(
                    formData {
                        append("file", wavData, Headers.build {
                            append(HttpHeaders.ContentType, "audio/wav")
                            append(HttpHeaders.ContentDisposition, "filename=\"chunk_$chunkIndex.wav\"")
                        })
                        append("model", model)
                        append("response_format", "verbose_json")

                        // Language (convert en-US to en)
                        val lang = config.language.split("-").first().lowercase()
                        if (lang != "auto") {
                            append("language", lang)
                        }
                    }
                ))
            }

            val elapsed = timeMark.elapsedNow()
            logger.debug { "<<< API response received in ${elapsed.inWholeMilliseconds}ms, status: ${response.status}" }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.debug { "Response body (first 500 chars): ${responseText.take(500)}" }
                parseResponseWithUsage(responseText, chunkIndex)
            } else {
                val errorText = response.bodyAsText()
                logger.error { "OpenAI API error: ${response.status}" }
                logger.error { "Error response: $errorText" }
                Pair(TranscriptionResult.Error(
                    message = "API error: ${response.status} - $errorText",
                    code = response.status.value.toString(),
                    isRecoverable = response.status.value in 500..599
                ), null)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation is expected when user stops - don't log as error
            logger.debug { "Chunk $chunkIndex transcription cancelled (user stopped)" }
            Pair(null, null)
        } catch (e: Exception) {
            logger.error(e) { "Failed to transcribe chunk $chunkIndex: ${e.message}" }
            logger.error { "Exception type: ${e::class.simpleName}" }
            Pair(TranscriptionResult.Error(
                message = "Transcription failed: ${e.message}",
                cause = e,
                isRecoverable = true
            ), null)
        }
    }
    
    private fun parseResponseWithUsage(responseText: String, chunkIndex: Int): Pair<TranscriptionResult?, ChunkUsage?> {
        return try {
            val response = json.decodeFromString<WhisperResponse>(responseText)
            
            // Calculate usage
            val durationSeconds = response.duration ?: chunkDurationSeconds.toDouble()
            val cost = (durationSeconds / 60.0) * WHISPER_COST_PER_MINUTE
            val usage = ChunkUsage(durationSeconds, cost)
            
            if (response.text.isBlank()) {
                return Pair(null, usage)
            }
            
            val words = response.words?.map { word ->
                TranscriptionResult.Word(
                    text = word.word,
                    confidence = 1f, // Whisper doesn't provide word-level confidence
                    startTime = word.start.seconds,
                    endTime = word.end.seconds
                )
            } ?: emptyList()
            
            val result = TranscriptionResult.Final(
                text = response.text,
                confidence = 1f, // Whisper doesn't provide overall confidence
                words = words,
                startTime = (chunkIndex * chunkDurationSeconds).seconds,
                endTime = ((chunkIndex + 1) * chunkDurationSeconds).seconds
            )
            
            Pair(result, usage)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse Whisper response: $responseText" }
            Pair(null, null)
        }
    }
    
    override fun release() {
        httpClient.close()
    }
}

// OpenAI Whisper response models
@Serializable
private data class WhisperResponse(
    val text: String = "",
    val language: String? = null,
    val duration: Double? = null,
    val words: List<WhisperWord>? = null,
    val segments: List<WhisperSegment>? = null
)

@Serializable
private data class WhisperWord(
    val word: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0
)

@Serializable
private data class WhisperSegment(
    val id: Int = 0,
    val text: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0
)

