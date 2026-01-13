package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import space.kodio.core.AudioFlow
import space.kodio.core.Kodio
import space.kodio.core.Recorder
import space.kodio.core.security.AudioPermissionManager
import space.kodio.transcription.*
import space.kodio.transcription.cloud.DeepgramEngine

/**
 * Demonstrates real-time transcription using Kodio's transcription extension.
 * 
 * This showcase:
 * - Records audio using Kodio's recorder
 * - Streams audio to Deepgram for real-time transcription
 * - Displays partial and final results as they arrive
 */
@Composable
fun TranscriptionShowcase(
    apiKey: String,
    modifier: Modifier = Modifier
) {
    var isTranscribing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var finalSegments by remember { mutableStateOf(listOf<TranscriptionSegment>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionState by remember { mutableStateOf(AudioPermissionManager.State.Unknown) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Create the transcription engine
    val engine = remember { DeepgramEngine(apiKey) }
    
    // Recorder reference for transcription
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var transcriptionJob by remember { mutableStateOf<Job?>(null) }
    
    // Check permission on launch
    LaunchedEffect(Unit) {
        permissionState = Kodio.microphonePermission.refresh()
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
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
        modifier = modifier.fillMaxSize().padding(16.dp),
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
            "Powered by Deepgram + Kodio",
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pulsing recording indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Red)
                    )
                    Text(
                        "Listening... Speak now",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
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
                    // Stop transcription
                    transcriptionJob?.cancel()
                    transcriptionJob = null
                    recorder?.stop()
                    recorder?.release()
                    recorder = null
                    isTranscribing = false
                } else {
                    // Start transcription
                    error = null
                    isTranscribing = true
                    
                    transcriptionJob = scope.launch {
                        try {
                            // Create a new recorder
                            val newRecorder = Kodio.recorder()
                            recorder = newRecorder
                            
                            // Start recording
                            newRecorder.start()
                            
                            // Get the live audio flow
                            val liveFlow = newRecorder.liveAudioFlow
                            val format = newRecorder.format
                            
                            if (liveFlow != null) {
                                val audioFlow = AudioFlow(format, liveFlow)
                                
                                audioFlow.transcribe(engine)
                                    .catch { e ->
                                        error = "Transcription error: ${e.message}"
                                        isTranscribing = false
                                        newRecorder.stop()
                                        newRecorder.release()
                                    }
                                    .collect { result ->
                                        when (result) {
                                            is TranscriptionResult.Partial -> {
                                                partialText = result.text
                                            }
                                            is TranscriptionResult.Final -> {
                                                if (result.text.isNotBlank()) {
                                                    finalSegments = finalSegments + TranscriptionSegment(
                                                        text = result.text,
                                                        confidence = result.confidence
                                                    )
                                                }
                                                partialText = ""
                                            }
                                            is TranscriptionResult.Error -> {
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
                                error = "Failed to get audio stream"
                                isTranscribing = false
                            }
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                            isTranscribing = false
                        }
                    }
                }
            },
            enabled = !needsPermission && apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTranscribing) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isTranscribing) "Stop Transcribing" else "Start Transcribing",
                style = MaterialTheme.typography.titleMedium
            )
        }
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

