package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import space.kodio.core.AudioFlow
import space.kodio.core.Kodio
import space.kodio.core.Recorder
import space.kodio.core.security.AudioPermissionManager
import space.kodio.transcription.*
import space.kodio.transcription.cloud.OpenAIWhisperEngine
import java.io.File

// Simple logging for debugging
private fun log(message: String) = println("[TranscriptionShowcase] $message")

// OpenAI Whisper pricing: $0.006 per minute
private const val WHISPER_COST_PER_MINUTE = 0.006

/**
 * Demonstrates transcription using Kodio's transcription extension.
 * Supports both live recording and file upload.
 */
@Composable
fun TranscriptionShowcase(
    apiKey: String,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("🎤 Live Recording", "📁 File Upload")
    
    Column(modifier = modifier.fillMaxSize()) {
        // Tab selector
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        // Content based on selected tab
        when (selectedTab) {
            0 -> LiveRecordingTab(apiKey = apiKey)
            1 -> FileUploadTab(apiKey = apiKey)
        }
    }
}

/**
 * Live recording transcription tab.
 */
@Composable
private fun LiveRecordingTab(apiKey: String) {
    var isTranscribing by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var finalSegments by remember { mutableStateOf(listOf<TranscriptionSegment>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionState by remember { mutableStateOf(AudioPermissionManager.State.Unknown) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Create the transcription engine (OpenAI Whisper - processes in chunks)
    // Shorter chunks = faster response but more API calls
    val engine = remember { OpenAIWhisperEngine(apiKey = apiKey, chunkDurationSeconds = 3) }
    
    // Recorder reference for transcription
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var transcriptionJob by remember { mutableStateOf<Job?>(null) }
    var audioForwardJob by remember { mutableStateOf<Job?>(null) }
    var audioChannel by remember { mutableStateOf<Channel<ByteArray>?>(null) }
    
    // Check permission on launch
    LaunchedEffect(Unit) {
        permissionState = Kodio.microphonePermission.refresh()
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioForwardJob?.cancel()
            audioChannel?.close()
            transcriptionJob?.cancel()
            recorder?.release()
            engine.release()
        }
    }
    
    // Auto-scroll to bottom when new segments arrive
    LaunchedEffect(finalSegments.size) {
        if (finalSegments.isNotEmpty()) {
            listState.animateScrollToItem(finalSegments.size - 1)
        }
    }
    
    val needsPermission = permissionState != AudioPermissionManager.State.Granted
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "Real-Time Transcription",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Powered by OpenAI Whisper + Kodio",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Permission handling
        if (needsPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Microphone permission required",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = { 
                        scope.launch {
                            Kodio.microphonePermission.request()
                            permissionState = Kodio.microphonePermission.refresh()
                        }
                    }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
        
        // Listening indicator
        AnimatedVisibility(
            visible = isTranscribing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFinishing) 
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    else 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Recording/finishing indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFinishing) Color(0xFFFFA500) else Color.Red)
                    )
                    Text(
                        if (isFinishing) "Finishing transcription..." else "Listening... Speak now",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFinishing) 
                            MaterialTheme.colorScheme.secondary 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Transcription results
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Results header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Transcript",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (finalSegments.isNotEmpty()) {
                        TextButton(
                            onClick = { 
                                finalSegments = emptyList()
                                partialText = ""
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
                
                // Transcript content
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Final segments
                        items(finalSegments) { segment ->
                            TranscriptionSegmentItem(segment)
                        }
                        
                        // Partial/interim text
                        if (partialText.isNotBlank()) {
                            item {
                                Text(
                                    text = partialText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        // Empty state
                        if (finalSegments.isEmpty() && partialText.isBlank() && !isTranscribing) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Press the button below to start transcribing",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Error display
        error?.let { errorMessage ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // Control button
        Button(
            onClick = {
                if (isTranscribing) {
                    // Stop recording - but let transcription finish processing buffered audio
                    log("=== Stopping Recording (will finish transcribing buffered audio) ===")
                    isFinishing = true
                    recorder?.stop()
                    audioForwardJob?.cancel() // Stop forwarding new audio
                    audioChannel?.close() // Close channel - this signals transcription flow to complete
                    log("Audio channel closed - transcription will process remaining buffer")
                    // Don't cancel transcriptionJob - let it finish naturally
                    // Don't set isTranscribing = false - the flow completion will do that
                } else {
                    // Start transcription
                    error = null
                    isTranscribing = true
                    
                    transcriptionJob = scope.launch {
                        try {
                            log("=== Starting Transcription ===")
                            log("API Key present: ${apiKey.isNotBlank()}, prefix: ${apiKey.take(10)}...")
                            
                            // Create a new recorder
                            log("Creating recorder...")
                            val newRecorder = Kodio.recorder()
                            recorder = newRecorder
                            log("Recorder created, format: ${newRecorder.format}")
                            
                            // Start recording
                            log("Starting recording...")
                            newRecorder.start()
                            log("Recording started!")
                            
                            // Get the live audio flow and forward through a channel
                            // The channel can be closed to signal end of audio (clean completion)
                            val liveFlow = newRecorder.liveAudioFlow
                            val format = newRecorder.format
                            
                            log("Live flow available: ${liveFlow != null}")
                            
                            if (liveFlow != null) {
                                // Create a channel that we can close to signal end of audio
                                val channel = Channel<ByteArray>(Channel.UNLIMITED)
                                audioChannel = channel
                                
                                // Forward audio from liveFlow to channel
                                audioForwardJob = scope.launch {
                                    try {
                                        liveFlow.collect { chunk ->
                                            channel.send(chunk)
                                        }
                                    } catch (e: Exception) {
                                        log("Audio forwarding ended: ${e.message}")
                                    }
                                }
                                
                                // Create AudioFlow from channel (will complete when channel is closed)
                                val audioFlow = AudioFlow(format, channel.consumeAsFlow())
                                log("AudioFlow created with channel-backed flow, format: $format")
                                
                                log("Starting transcription flow...")
                                audioFlow.transcribe(engine)
                                    .onStart { log("Transcription flow started") }
                                    .onCompletion { cause -> 
                                        if (cause == null) {
                                            log("Transcription flow completed successfully!")
                                        } else if (cause is kotlinx.coroutines.CancellationException) {
                                            log("Transcription flow cancelled")
                                        } else {
                                            log("Transcription flow completed with error: $cause")
                                        }
                                        // Clean up after flow completes naturally
                                        log("Cleaning up after transcription...")
                                        audioForwardJob?.cancel()
                                        audioForwardJob = null
                                        audioChannel?.close()
                                        audioChannel = null
                                        recorder?.release()
                                        recorder = null
                                        transcriptionJob = null
                                        isTranscribing = false
                                        isFinishing = false
                                    }
                                    .catch { e ->
                                        // Don't treat cancellation as an error
                                        if (e is kotlinx.coroutines.CancellationException) {
                                            log("Transcription cancelled")
                                        } else {
                                            log("ERROR: Transcription error in catch: ${e.message}")
                                            e.printStackTrace()
                                            error = "Transcription error: ${e.message}"
                                        }
                                        // Note: cleanup is handled in onCompletion
                                    }
                                    .collect { result ->
                                        log("Received transcription result: $result")
                                        when (result) {
                                            is TranscriptionResult.Partial -> {
                                                log("Partial: ${result.text}")
                                                partialText = result.text
                                            }
                                            is TranscriptionResult.Final -> {
                                                log("Final: ${result.text}")
                                                if (result.text.isNotBlank()) {
                                                    finalSegments = finalSegments + TranscriptionSegment(
                                                        text = result.text,
                                                        confidence = result.confidence
                                                    )
                                                }
                                                partialText = ""
                                            }
                                            is TranscriptionResult.Error -> {
                                                log("ERROR result: ${result.message}")
                                                error = result.message
                                                if (!result.isRecoverable) {
                                                    isTranscribing = false
                                                    newRecorder.stop()
                                                    newRecorder.release()
                                                }
                                            }
                                        }
                                    }
                            } else {
                                log("ERROR: Failed to get live audio flow!")
                                error = "Failed to get audio stream"
                                isTranscribing = false
                            }
                        } catch (e: Exception) {
                            // Ignore cancellation exceptions (expected when stopping)
                            if (e is kotlinx.coroutines.CancellationException) {
                                log("Transcription cancelled (user stopped)")
                            } else {
                                log("ERROR: Exception in transcription: ${e.message}")
                                e.printStackTrace()
                                error = "Error: ${e.message}"
                            }
                            // Ensure cleanup on any exception
                            audioForwardJob?.cancel()
                            audioForwardJob = null
                            audioChannel?.close()
                            audioChannel = null
                            recorder?.release()
                            recorder = null
                            transcriptionJob = null
                            isTranscribing = false
                            isFinishing = false
                        }
                    }
                }
            },
            enabled = !needsPermission && apiKey.isNotBlank() && !isFinishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    isFinishing -> MaterialTheme.colorScheme.secondary
                    isTranscribing -> MaterialTheme.colorScheme.error 
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = when {
                    isFinishing -> "Finishing..."
                    isTranscribing -> "Stop Transcribing"
                    else -> "Start Transcribing"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * File upload transcription tab.
 */
@Composable
private fun FileUploadTab(apiKey: String) {
    var isTranscribing by remember { mutableStateOf(false) }
    var transcriptionResult by remember { mutableStateOf<FileTranscriptionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "File Transcription",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // File picker button
        Button(
            onClick = {
                // Use AWT file dialog for desktop
                scope.launch(Dispatchers.IO) {
                    try {
                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Audio File", java.awt.FileDialog.LOAD)
                        dialog.setFilenameFilter { _, name -> 
                            val ext = name.lowercase().substringAfterLast('.')
                            ext in listOf("mp3", "wav", "m4a", "mp4", "webm", "ogg", "oga", "flac", "mpeg", "mpga")
                        }
                        dialog.isVisible = true
                        
                        val directory = dialog.directory
                        val fileName = dialog.file
                        
                        if (directory != null && fileName != null) {
                            val file = File(directory, fileName)
                            selectedFileName = file.name
                            log("File selected: ${file.absolutePath}")
                            
                            withContext(Dispatchers.Main) {
                                isTranscribing = true
                                error = null
                                transcriptionResult = null
                            }
                            
                            try {
                                val result = transcribeFile(file, apiKey)
                                withContext(Dispatchers.Main) {
                                    transcriptionResult = result
                                }
                                log("Transcription complete: ${result.text.take(100)}...")
                            } catch (e: Exception) {
                                log("Transcription error: ${e.message}")
                                withContext(Dispatchers.Main) {
                                    error = e.message ?: "Unknown error"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isTranscribing = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            error = "Failed to open file: ${e.message}"
                        }
                    }
                }
            },
            enabled = !isTranscribing && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📁 Select Audio File", style = MaterialTheme.typography.titleMedium)
                Text(
                    "MP3, WAV, M4A, MP4, WEBM, OGG, FLAC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        }
        
        // Processing indicator
        if (isTranscribing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            "Transcribing: ${selectedFileName ?: "file"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "This may take a moment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Error display
        error?.let { errorMessage ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // Results
        transcriptionResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Results header with cost
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Transcript",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Duration badge
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⏱️ ${String.format("%.1f", result.durationSeconds)}s",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            // Cost badge
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "💰 $${String.format("%.4f", result.cost)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    
                    // Transcript content
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            item {
                                Text(
                                    text = result.text,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Empty state when no result
        if (transcriptionResult == null && !isTranscribing && error == null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Drop an audio file above to transcribe it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
    
}

/**
 * Result from file transcription.
 */
private data class FileTranscriptionResult(
    val text: String,
    val durationSeconds: Double,
    val cost: Double,
    val language: String
)

/**
 * Transcribes a file using OpenAI Whisper API.
 */
private suspend fun transcribeFile(
    file: File,
    apiKey: String
): FileTranscriptionResult = withContext(Dispatchers.IO) {
    log("Transcribing file: ${file.name} (${file.length()} bytes)")
    
    val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
    val url = java.net.URL("https://api.openai.com/v1/audio/transcriptions")
    val connection = url.openConnection() as java.net.HttpURLConnection
    
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        
        val outputStream = connection.outputStream
        val writer = outputStream.bufferedWriter()
        
        // File part
        writer.write("--$boundary\r\n")
        writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
        writer.write("Content-Type: ${guessContentType(file.name)}\r\n")
        writer.write("\r\n")
        writer.flush()
        file.inputStream().copyTo(outputStream)
        outputStream.flush()
        writer.write("\r\n")
        
        // Model part
        writer.write("--$boundary\r\n")
        writer.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
        writer.write("whisper-1\r\n")
        
        // Response format part
        writer.write("--$boundary\r\n")
        writer.write("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
        writer.write("verbose_json\r\n")
        
        // End boundary
        writer.write("--$boundary--\r\n")
        writer.flush()
        writer.close()
        
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("API error $responseCode: $errorText")
        }
        
        log("API response: ${responseText.take(200)}...")
        
        // Parse JSON manually (simple extraction)
        val text = extractJsonString(responseText, "text") ?: ""
        val language = extractJsonString(responseText, "language") ?: "unknown"
        val duration = extractJsonNumber(responseText, "duration") ?: 0.0
        val cost = (duration / 60.0) * WHISPER_COST_PER_MINUTE
        
        FileTranscriptionResult(
            text = text,
            durationSeconds = duration,
            cost = cost,
            language = language
        )
    } finally {
        connection.disconnect()
    }
}

/**
 * Simple JSON string extraction (avoids adding serialization dependency).
 */
private fun extractJsonString(json: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
    val regex = Regex(pattern)
    return regex.find(json)?.groupValues?.get(1)
}

/**
 * Simple JSON number extraction.
 */
private fun extractJsonNumber(json: String, key: String): Double? {
    val pattern = "\"$key\"\\s*:\\s*([0-9.]+)"
    val regex = Regex(pattern)
    return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
}

/**
 * Guesses content type from file extension.
 */
private fun guessContentType(fileName: String): String {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/m4a"
        "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "audio/mpeg"
    }
}

/**
 * A single transcription segment with confidence indicator.
 */
@Composable
private fun TranscriptionSegmentItem(segment: TranscriptionSegment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Confidence indicator
        val confidenceColor = when {
            segment.confidence >= 0.9f -> Color(0xFF4CAF50) // Green
            segment.confidence >= 0.7f -> Color(0xFFFFC107) // Yellow
            else -> Color(0xFFFF5722) // Orange
        }
        
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(confidenceColor)
        )
        
        Text(
            text = segment.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Represents a finalized transcription segment.
 */
data class TranscriptionSegment(
    val text: String,
    val confidence: Float
)

