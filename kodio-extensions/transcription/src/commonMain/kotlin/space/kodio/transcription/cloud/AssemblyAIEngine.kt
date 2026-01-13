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
import space.kodio.core.SampleEncoding
import space.kodio.transcription.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

/**
 * AssemblyAI real-time speech-to-text engine.
 * 
 * Uses AssemblyAI's WebSocket streaming API for real-time transcription.
 * Requires audio to be 16kHz mono PCM16.
 * 
 * ## Example
 * ```kotlin
 * val engine = AssemblyAIEngine(apiKey = "your-assemblyai-api-key")
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
 * @param apiKey Your AssemblyAI API key
 * @param httpClient Optional custom HTTP client
 */
class AssemblyAIEngine(
    private val apiKey: String,
    private val httpClient: HttpClient = createDefaultClient()
) : TranscriptionEngine {
    
    override val provider = TranscriptionProvider.ASSEMBLY_AI
    
    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    override fun transcribe(
        audioFlow: AudioFlow,
        config: TranscriptionConfig
    ): Flow<TranscriptionResult> = flow {
        if (!isAvailable) {
            emit(TranscriptionResult.Error(
                message = "AssemblyAI API key not configured",
                code = "NO_API_KEY",
                isRecoverable = false
            ))
            return@flow
        }
        
        // Validate audio format - AssemblyAI requires 16kHz mono PCM16
        if (!isValidFormat(audioFlow.format)) {
            emit(TranscriptionResult.Error(
                message = "AssemblyAI requires 16kHz mono PCM16 audio. " +
                         "Current format: ${audioFlow.format.sampleRate}Hz, " +
                         "${audioFlow.format.channels.count} channel(s)",
                code = "INVALID_FORMAT",
                isRecoverable = false
            ))
            return@flow
        }
        
        val wsUrl = buildWebSocketUrl(config)
        logger.info { "Connecting to AssemblyAI: $wsUrl" }
        
        try {
            httpClient.webSocket(
                urlString = wsUrl,
                request = {
                    headers.append("Authorization", apiKey)
                }
            ) {
                logger.info { "WebSocket connected to AssemblyAI" }
                
                // Wait for session begin message
                val beginFrame = incoming.receive()
                if (beginFrame is Frame.Text) {
                    val text = beginFrame.readText()
                    logger.debug { "Session begin: $text" }
                }
                
                // Launch coroutine to send audio data
                val sendJob = launch {
                    try {
                        audioFlow.collect { audioChunk ->
                            // AssemblyAI expects base64 encoded audio in JSON
                            val base64Audio = Base64.encode(audioChunk)
                            val message = """{"audio_data": "$base64Audio"}"""
                            send(Frame.Text(message))
                        }
                        // Signal end of audio stream
                        send(Frame.Text("""{"terminate_session": true}"""))
                        logger.debug { "Audio stream completed, sent terminate_session" }
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
                                    // Check if session ended
                                    if (text.contains("SessionTerminated")) {
                                        break
                                    }
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
    
    private fun isValidFormat(format: AudioFormat): Boolean {
        // AssemblyAI requires 16kHz mono PCM16 for real-time
        // Allow some flexibility with sample rates (8kHz-48kHz work)
        val encoding = format.encoding
        return encoding is SampleEncoding.PcmInt && encoding.bitDepth.bits == 16
    }
    
    private fun buildWebSocketUrl(config: TranscriptionConfig): String {
        val params = buildList {
            add("sample_rate=16000") // AssemblyAI real-time uses 16kHz
            
            if (config.diarization) {
                add("speaker_labels=true")
            }
            
            if (config.keywords.isNotEmpty()) {
                add("word_boost=${config.keywords.joinToString(",")}")
            }
        }
        
        return "wss://api.assemblyai.com/v2/realtime/ws?${params.joinToString("&")}"
    }
    
    private fun parseResponse(text: String): TranscriptionResult? {
        return try {
            val response = json.decodeFromString<AssemblyAIResponse>(text)
            
            // Check message type
            when (response.message_type) {
                "SessionBegins" -> {
                    logger.info { "AssemblyAI session started: ${response.session_id}" }
                    null
                }
                "SessionTerminated" -> {
                    logger.info { "AssemblyAI session terminated" }
                    null
                }
                "PartialTranscript" -> {
                    if (response.text.isNullOrBlank()) null
                    else TranscriptionResult.Partial(
                        text = response.text,
                        confidence = response.confidence ?: 0f
                    )
                }
                "FinalTranscript" -> {
                    if (response.text.isNullOrBlank()) null
                    else {
                        val words = response.words?.map { word ->
                            TranscriptionResult.Word(
                                text = word.text,
                                confidence = word.confidence,
                                startTime = word.start.milliseconds,
                                endTime = word.end.milliseconds
                            )
                        } ?: emptyList()
                        
                        TranscriptionResult.Final(
                            text = response.text,
                            confidence = response.confidence ?: 0f,
                            words = words,
                            startTime = response.audio_start?.milliseconds,
                            endTime = response.audio_end?.milliseconds
                        )
                    }
                }
                "SessionInformation" -> {
                    logger.debug { "Session info: $text" }
                    null
                }
                else -> {
                    // Check for errors
                    if (response.error != null) {
                        TranscriptionResult.Error(
                            message = response.error,
                            isRecoverable = false
                        )
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse AssemblyAI response: $text" }
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

// AssemblyAI response models
@Serializable
private data class AssemblyAIResponse(
    val message_type: String? = null,
    val session_id: String? = null,
    val text: String? = null,
    val confidence: Float? = null,
    val words: List<AssemblyAIWord>? = null,
    val audio_start: Long? = null,
    val audio_end: Long? = null,
    val error: String? = null
)

@Serializable
private data class AssemblyAIWord(
    val text: String = "",
    val start: Long = 0,
    val end: Long = 0,
    val confidence: Float = 0f
)

