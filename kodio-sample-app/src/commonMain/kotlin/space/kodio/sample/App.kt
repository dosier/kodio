package space.kodio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import space.kodio.compose.material3.component.PlayAudioButton
import space.kodio.compose.material3.component.RecordAudioButton
import space.kodio.compose.rememberRecordAudioState
import space.kodio.core.AudioFlow

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            var recordings by remember { mutableStateOf(listOf<AudioFlow>())}
            val state = rememberRecordAudioState(
                preferredInput = null,
                onFinishRecording = { audioRecording: AudioFlow ->
                    recordings += audioRecording
                }
            )
            val scope = rememberCoroutineScope()
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { RecordAudioButton(state) }
                item { HorizontalDivider() }
                items(recordings) { audioFrames ->
                    ListItem(
                        headlineContent =  {
                            PlayAudioButton(audioFrames)
                        },
                        supportingContent = {
                            TextButton(onClick = { scope.launch { saveWavFile(audioFrames) } }) {
                                Text("Save as wav")
                            }
                        }
                    )
                }
            }
        }
    }
}

