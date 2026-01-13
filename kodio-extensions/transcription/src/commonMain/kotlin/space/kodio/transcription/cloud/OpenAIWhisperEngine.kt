package space.kodio.transcription.cloud

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.kodio.core.*
import space.kodio.transcription.*
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * OpenAI Whisper API transcription engine.
 * 
 * Note: This is NOT true real-time streaming. OpenAI's Whisper API processes
 * complete audio files, so this engine buffers audio and sends it in chunks.
 * For true real-time streaming, use [DeepgramEngine] or [AssemblyAIEngine].
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
 * @param apiKey Your OpenAI API key
 * @param model The Whisper model to use (default: "whisper-1")
 * @param httpClient Optional custom HTTP client
 * @param chunkDurationSeconds Duration in seconds for each chunk (for streaming mode)
 */
class OpenAIWhisperEngine(
    private val apiKey: String,
    private val model: String = "whisper-1",
    private val httpClient: HttpClient = HttpClient(),
    private val chunkDurationSeconds: Int = 10
) : TranscriptionEngine {
    
    override val provider = TranscriptionProvider.OPENAI_WHISPER
    
    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flow {
        logger.info { "=== OpenAI Whisper Transcription Starting ===" }
        logger.info { "API Key present: ${apiKey.isNotBlank()}, Key prefix: ${apiKey.take(10)}..." }
        logger.info { "Model: $model, Chunk duration: ${chunkDurationSeconds}s" }
        
        if (!isAvailable) {
            logger.error { "API key not configured!" }
            emit(TranscriptionResult.Error(
                message = "OpenAI API key not configured",
                code = "NO_API_KEY",
                isRecoverable = false
            ))
            return@flow
        }
        
        val format = audioFlow.format
        val bytesPerSecond = format.sampleRate * format.bytesPerFrame
        val chunkSize = bytesPerSecond * chunkDurationSeconds
        
        logger.info { "Audio format: sampleRate=${format.sampleRate}, channels=${format.channels}, encoding=${format.encoding}" }
        logger.info { "Bytes per second: $bytesPerSecond, Chunk size target: $chunkSize bytes (${chunkDurationSeconds}s)" }
        
        var buffer = Buffer()
        var chunkIndex = 0
        var totalBytesReceived = 0L
        var audioChunkCount = 0
        var totalSecondsTranscribed = 0.0
        var totalCost = 0.0
        
        logger.info { "Starting to collect audio flow..." }
        
        audioFlow.collect { audioChunk ->
            audioChunkCount++
            totalBytesReceived += audioChunk.size
            buffer.write(audioChunk)
            
            if (audioChunkCount <= 5 || audioChunkCount % 50 == 0) {
                logger.debug { "Audio chunk #$audioChunkCount: ${audioChunk.size} bytes, buffer: ${buffer.size}/$chunkSize bytes, total received: $totalBytesReceived" }
            }
            
            // When we have enough data, send a chunk
            if (buffer.size >= chunkSize) {
                logger.info { ">>> Buffer full! Preparing chunk $chunkIndex for transcription..." }
                val chunkData = buffer.readByteArray(chunkSize.toInt())
                buffer = Buffer().apply { 
                    // Keep remaining data
                    val remaining = buffer.readByteArray()
                    write(remaining)
                }
                
                // Create WAV data for this chunk
                val wavData = createWavData(format, chunkData)
                logger.info { "Created WAV data: ${wavData.size} bytes (PCM: ${chunkData.size} bytes)" }
                
                val (result, usage) = transcribeChunk(wavData, config, chunkIndex)
                if (usage != null) {
                    totalSecondsTranscribed += usage.seconds
                    totalCost += usage.cost
                    logger.info { "Chunk $chunkIndex: ${usage.seconds}s transcribed, cost: \$${String.format("%.4f", usage.cost)}" }
                }
                logger.info { "Chunk $chunkIndex result: $result" }
                if (result != null) {
                    emit(result)
                }
                chunkIndex++
            }
        }
        
        logger.info { "Audio flow collection ended. Total chunks received: $audioChunkCount, Total bytes: $totalBytesReceived" }
        logger.info { "Remaining buffer size: ${buffer.size} bytes" }
        
        // Process remaining audio
        if (buffer.size > 0) {
            logger.info { "Processing remaining ${buffer.size} bytes as final chunk..." }
            val remainingData = buffer.readByteArray()
            val wavData = createWavData(format, remainingData)
            logger.info { "Created final WAV data: ${wavData.size} bytes" }
            val (result, usage) = transcribeChunk(wavData, config, chunkIndex)
            if (usage != null) {
                totalSecondsTranscribed += usage.seconds
                totalCost += usage.cost
                logger.info { "Final chunk: ${usage.seconds}s transcribed, cost: \$${String.format("%.4f", usage.cost)}" }
            }
            logger.info { "Final chunk result: $result" }
            if (result != null) {
                emit(result)
            }
        } else {
            logger.warn { "No remaining audio data to process" }
        }
        
        logger.info { "=== OpenAI Whisper Transcription Complete ===" }
        logger.info { "📊 USAGE SUMMARY:" }
        logger.info { "   Total audio transcribed: ${totalSecondsTranscribed}s (${String.format("%.2f", totalSecondsTranscribed / 60)} minutes)" }
        logger.info { "   Total cost: \$${String.format("%.4f", totalCost)}" }
        logger.info { "   Chunks processed: $chunkIndex" }
    }
    
    private fun createWavData(format: AudioFormat, pcmData: ByteArray): ByteArray {
        val buffer = Buffer()
        
        // Derive WAV header fields
        val numChannels = format.channels.count
        val sampleRate = format.sampleRate
        
        val (audioFormatCode, bitsPerSample) = when (val enc = format.encoding) {
            is SampleEncoding.PcmInt -> 1 to enc.bitDepth.bits
            is SampleEncoding.PcmFloat -> 3 to when (enc.precision) {
                FloatPrecision.F32 -> 32
                FloatPrecision.F64 -> 64
            }
        }
        
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = numChannels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val dataSize = pcmData.size
        val riffSize = 36 + dataSize
        
        // Write RIFF/WAVE header
        buffer.writeString("RIFF")
        buffer.writeIntLe(riffSize)
        buffer.writeString("WAVE")
        
        // fmt subchunk
        buffer.writeString("fmt ")
        buffer.writeIntLe(16)
        buffer.writeShortLe(audioFormatCode.toShort())
        buffer.writeShortLe(numChannels.toShort())
        buffer.writeIntLe(sampleRate)
        buffer.writeIntLe(byteRate)
        buffer.writeShortLe(blockAlign.toShort())
        buffer.writeShortLe(bitsPerSample.toShort())
        
        // data subchunk
        buffer.writeString("data")
        buffer.writeIntLe(dataSize)
        
        // Write PCM data
        buffer.write(pcmData)
        
        return buffer.readByteArray()
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
        return try {
            logger.info { ">>> Sending chunk $chunkIndex (${wavData.size} bytes) to OpenAI Whisper API..." }
            logger.debug { "Config: language=${config.language}, model=$model" }
            
            val startTime = System.currentTimeMillis()
            
            val response = httpClient.post("https://api.openai.com/v1/audio/transcriptions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
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
            
            val elapsed = System.currentTimeMillis() - startTime
            logger.info { "<<< API response received in ${elapsed}ms, status: ${response.status}" }
            
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.info { "Response body (first 500 chars): ${responseText.take(500)}" }
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
            logger.info { "Chunk $chunkIndex transcription cancelled (user stopped)" }
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

