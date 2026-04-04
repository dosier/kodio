package space.kodio.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import space.kodio.compose.AudioWaveform
import space.kodio.compose.PlayerState
import space.kodio.compose.RecorderState
import space.kodio.compose.WaveformColors
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberPlayerState
import space.kodio.compose.rememberRecorderState
import space.kodio.core.AudioFormat
import space.kodio.core.AudioQuality
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.SampleEncoding

/**
 * Sample app demonstrating Kodio features.
 * 
 * This showcases:
 * - Recording with rememberRecorderState()
 * - Playback with rememberPlayerState()  
 * - AudioWaveform visualization
 * - Real-time transcription with OpenAI Whisper
 */

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var selectedTab by remember { mutableStateOf(0) }
        
        // API Key - try to get from system property first (set via local.properties)
        var apiKey by remember { mutableStateOf(getOpenAIApiKey()) }
        var showApiKeyDialog by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                Column {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Recording") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Playback") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Transcription") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> RecordingDemo()
                    1 -> PlaybackShowcase()
                    2 -> {
                        if (apiKey.isBlank()) {
                            // Show API key input
                            ApiKeyInputScreen(
                                onApiKeySubmit = { key ->
                                    apiKey = key
                                }
                            )
                        } else {
                            TranscriptionShowcase(apiKey = apiKey)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen to input the OpenAI API key.
 */
@Composable
private fun ApiKeyInputScreen(
    onApiKeySubmit: (String) -> Unit
) {
    var inputKey by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "OpenAI API Key Required",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            "Enter your OpenAI API key to enable transcription.\n" +
            "Get a key at platform.openai.com",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = inputKey,
            onValueChange = { inputKey = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { onApiKeySubmit(inputKey) },
            enabled = inputKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

/**
 * The original recording demo section.
 */
@Composable
private fun RecordingDemo() {
    var recordings by remember { mutableStateOf(listOf<AudioRecording>()) }
    var selectedQuality by remember { mutableStateOf(AudioQuality.Default) }
    
    val recorderState = rememberRecorderState(
        quality = selectedQuality,
        onRecordingComplete = { recording ->
            recordings = recordings + recording
        }
    )
    
    val scope = rememberCoroutineScope()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            QualitySelector(
                selected = selectedQuality,
                onSelect = { selectedQuality = it },
                enabled = !recorderState.isRecording,
            )
        }

        item {
            RecordingSection(recorderState, scope)
        }
        
        item { 
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) 
        }
        
        item {
            Text(
                "Recordings (${recordings.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        items(recordings) { recording ->
            RecordingItem(
                recording = recording,
                onSave = { scope.launch { saveWavFile(recording.asAudioFlow()) } },
                onDelete = { recordings = recordings - recording }
            )
        }
    }
}

@Composable
private fun RecordingSection(
    recorderState: RecorderState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status text
            Text(
                text = when {
                    recorderState.needsPermission -> "Microphone permission required"
                    recorderState.isRecording -> "Recording..."
                    recorderState.error != null -> "Error: ${recorderState.error?.message}"
                    else -> "Ready to record"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Permission button if needed
            if (recorderState.needsPermission) {
                Button(onClick = { recorderState.requestPermission() }) {
                    Text("Grant Permission")
                }
            }
            
            // Waveform visualization (using new mirrored style for recording)
            if (recorderState.isRecording) {
                AudioWaveform(
                    amplitudes = recorderState.liveAmplitudes,
                    style = WaveformStyle.Mirrored(),
                    colors = WaveformColors.GreenGradient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
            
            // Record button
            Button(
                onClick = { recorderState.toggle() },
                enabled = !recorderState.needsPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recorderState.isRecording) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (recorderState.isRecording) "Stop Recording" else "Start Recording")
            }
        }
    }
}

@Composable
private fun QualitySelector(
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Audio Quality", style = MaterialTheme.typography.titleSmall)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEachIndexed { index, quality ->
                    SegmentedButton(
                        selected = quality == selected,
                        onClick = { onSelect(quality) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AudioQuality.entries.size
                        ),
                    ) {
                        Text(quality.name, maxLines = 1)
                    }
                }
            }

            Text(
                text = selected.format.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordingItem(
    recording: AudioRecording,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val playerState = rememberPlayerState(recording)
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { playerState.toggle() }) {
                    Text(if (playerState.isPlaying) "⏸" else "▶️")
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Duration: ${recording.calculatedDuration}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        recording.format.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(recording.sizeInBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                TextButton(onClick = onSave) { Text("Save") }
                IconButton(onClick = onDelete) { Text("🗑️") }
            }
        }
    }
}

private val AudioFormat.label: String
    get() = buildString {
        append("${sampleRate / 1000.0}kHz")
        append(" / ")
        append(if (channels == Channels.Mono) "Mono" else "Stereo")
        append(" / ")
        when (val enc = encoding) {
            is SampleEncoding.PcmInt -> append("${enc.bitDepth.bits}-bit int")
            is SampleEncoding.PcmFloat -> append("${if (enc.precision == space.kodio.core.FloatPrecision.F32) "32" else "64"}-bit float")
        }
    }

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
}
