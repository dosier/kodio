package space.kodio.transcription.cloud

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.Channels
import space.kodio.core.SampleEncoding
import space.kodio.transcription.*
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Deepgram real-time speech-to-text engine.
 * 
 * Uses Deepgram's WebSocket streaming API for low-latency transcription.
 * Supports interim results, punctuation, and multiple languages.
 * 
 * ## Example
 * ```kotlin
 * val engine = DeepgramEngine(apiKey = "your-deepgram-api-key")
 * 
 * audioFlow.transcribe(engine).collect { result ->
 *     when (result) {
 *         is TranscriptionResult.Partial -> println("Interim: ${result.text}")
 *         is TranscriptionResult.Final -> println("Final: ${result.text}")
 *         is TranscriptionResult.Error -> println("Error: ${result.message}")
 *     }
 * }
 * ```
 * 
 * @param apiKey Your Deepgram API key
 * @param httpClient Optional custom HTTP client (defaults to a new client with WebSocket support)
 */
class DeepgramEngine(
    private val apiKey: String,
    private val httpClient: HttpClient = createDefaultClient()
) : TranscriptionEngine {
    
    override val provider = TranscriptionProvider.DEEPGRAM
    
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
        if (!isAvailable) {
            emit(TranscriptionResult.Error(
                message = "Deepgram API key not configured",
                code = "NO_API_KEY",
                isRecoverable = false
            ))
            return@flow
        }
        
        val wsUrl = buildWebSocketUrl(audioFlow.format, config)
        logger.info { "Connecting to Deepgram: $wsUrl" }
        
        try {
            httpClient.webSocket(
                urlString = wsUrl,
                request = {
                    headers.append("Authorization", "Token $apiKey")
                }
            ) {
                logger.info { "WebSocket connected to Deepgram" }
                
                // Launch coroutine to send audio data
                val sendJob = launch {
                    try {
                        audioFlow.collect { audioChunk ->
                            send(Frame.Binary(true, audioChunk))
                        }
                        // Signal end of audio stream
                        send(Frame.Text("""{"type": "CloseStream"}"""))
                        logger.debug { "Audio stream completed, sent CloseStream" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error sending audio data" }
                    }
                }
                
                // Receive transcription results
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                val result = parseResponse(text)
                                if (result != null) {
                                    emit(result)
                                }
                            }
                            is Frame.Close -> {
                                logger.info { "WebSocket closed by server" }
                                break
                            }
                            else -> { /* Ignore other frame types */ }
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    logger.debug { "WebSocket channel closed" }
                }
                
                sendJob.cancel()
            }
        } catch (e: Exception) {
            logger.error(e) { "WebSocket error" }
            emit(TranscriptionResult.Error(
                message = "Connection error: ${e.message}",
                cause = e,
                isRecoverable = false
            ))
        }
    }
    
    private fun buildWebSocketUrl(format: AudioFormat, config: TranscriptionConfig): String {
        val params = buildList {
            // Audio encoding
            add("encoding=${getEncoding(format)}")
            add("sample_rate=${format.sampleRate}")
            add("channels=${format.channels.count}")
            
            // Transcription options
            add("language=${config.language.lowercase().replace("-", "_")}")
            add("punctuate=${config.punctuation}")
            add("interim_results=${config.interimResults}")
            
            if (config.profanityFilter) {
                add("profanity_filter=true")
            }
            
            if (config.diarization) {
                add("diarize=true")
            }
            
            config.model?.let { add("model=$it") }
            
            if (config.keywords.isNotEmpty()) {
                config.keywords.forEach { keyword ->
                    add("keywords=${keyword.encodeURLParameter()}")
                }
            }
        }
        
        return "wss://api.deepgram.com/v1/listen?${params.joinToString("&")}"
    }
    
    private fun getEncoding(format: AudioFormat): String {
        return when (val encoding = format.encoding) {
            is SampleEncoding.PcmInt -> {
                when (encoding.bitDepth.bits) {
                    16 -> "linear16"
                    8 -> if (encoding.signed) "linear8" else "mulaw"
                    else -> "linear16"
                }
            }
            is SampleEncoding.PcmFloat -> "linear32"
        }
    }
    
    private fun parseResponse(text: String): TranscriptionResult? {
        return try {
            val response = json.decodeFromString<DeepgramResponse>(text)
            
            // Check for errors
            if (response.error != null) {
                return TranscriptionResult.Error(
                    message = response.error,
                    code = response.errorCode,
                    isRecoverable = false
                )
            }
            
            // Get the first channel's first alternative
            val channel = response.channel ?: return null
            val alternative = channel.alternatives.firstOrNull() ?: return null
            
            if (alternative.transcript.isBlank()) {
                return null
            }
            
            val words = alternative.words.map { word ->
                TranscriptionResult.Word(
                    text = word.word,
                    confidence = word.confidence,
                    startTime = word.start.seconds,
                    endTime = word.end.seconds
                )
            }
            
            if (response.isFinal == true) {
                TranscriptionResult.Final(
                    text = alternative.transcript,
                    confidence = alternative.confidence,
                    words = words,
                    startTime = response.start?.seconds,
                    endTime = (response.start?.plus(response.duration ?: 0.0))?.seconds
                )
            } else {
                TranscriptionResult.Partial(
                    text = alternative.transcript,
                    confidence = alternative.confidence
                )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse Deepgram response: $text" }
            null
        }
    }
    
    override fun release() {
        httpClient.close()
    }
    
    companion object {
        private fun createDefaultClient(): HttpClient {
            return HttpClient {
                install(WebSockets)
            }
        }
    }
}

// Deepgram response models
@Serializable
private data class DeepgramResponse(
    val type: String? = null,
    val channel: DeepgramChannel? = null,
    val is_final: Boolean? = null,
    val start: Double? = null,
    val duration: Double? = null,
    val error: String? = null,
    val error_code: String? = null
) {
    val isFinal: Boolean? get() = is_final
    val errorCode: String? get() = error_code
}

@Serializable
private data class DeepgramChannel(
    val alternatives: List<DeepgramAlternative> = emptyList()
)

@Serializable
private data class DeepgramAlternative(
    val transcript: String = "",
    val confidence: Float = 0f,
    val words: List<DeepgramWord> = emptyList()
)

@Serializable
private data class DeepgramWord(
    val word: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0,
    val confidence: Float = 0f
)

// URL encoding helper
private fun String.encodeURLParameter(): String {
    return this.map { char ->
        when {
            char.isLetterOrDigit() || char in "-_.~" -> char.toString()
            else -> "%${char.code.toString(16).uppercase().padStart(2, '0')}"
        }
    }.joinToString("")
}

