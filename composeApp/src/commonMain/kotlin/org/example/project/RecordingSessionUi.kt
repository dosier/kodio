package org.example.project

import AudioPermissionDeniedException
import AudioRecordingSession
import AudioRecordingState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RecordingSessionUi(
    audioRecordingSession: AudioRecordingSession
) {
    val state by audioRecordingSession.state.collectAsState()
    val audioFlow by audioRecordingSession.audioFlow.collectAsState()
    var audioFrameCount by remember { mutableStateOf(0) }
    LaunchedEffect(audioFlow) {
        audioFrameCount = 0
        audioFlow?.collect {
            audioFrameCount++
        }
    }

    val scope = rememberCoroutineScope()
    Row {
        AnimatedContent(state) {
            when (it) {
                AudioRecordingState.Recording -> {
                    TextButton(onClick = { audioRecordingSession.stop() }) {
                        Text("Stop Recording (${audioFrameCount} frames)")
                    }
                }
                AudioRecordingState.Idle,
                is AudioRecordingState.Stopped -> {
                    TextButton(onClick = { audioRecordingSession.start(scope) }) {
                        Text("Start Recording")
                    }
                }
                is AudioRecordingState.Error -> {
                    Column {
                        Text("Error: ${it.error.message}")
                        TextButton(onClick = { audioRecordingSession.start(scope) }) {
                            Text("Start Recording")
                        }
                    }
                }
            }
        }
    }
}

private fun AudioRecordingSession.start(scope: CoroutineScope) {
    scope.launch {
        try {
            start()
        } catch (e: AudioPermissionDeniedException) {

        }
    }
}