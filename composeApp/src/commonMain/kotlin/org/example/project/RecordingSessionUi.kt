package org.example.project

import AudioFormat
import RecordingSession
import RecordingState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch


@Composable
fun RecordingSessionUi(
    recordingSession: RecordingSession,
    format: AudioFormat,
    onStopRecording: (Flow<ByteArray>, AudioFormat) -> Unit = { data, format -> }
) {
    val state by recordingSession.state.collectAsState()
    var audioFrames by remember {
        mutableStateOf(emptyList<ByteArray>())
    }
    val audioFormat by recordingSession.actualFormat.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(recordingSession) {
        recordingSession.audioDataFlow.collect {
            audioFrames = audioFrames + it
        }
    }
    Row {
        AnimatedContent(state) {
            when (it) {
                RecordingState.Recording -> {
                    TextButton(
                        onClick = {
                            recordingSession.stop()
                            onStopRecording(audioFrames.asFlow(), audioFormat?:format)
                        }
                    ) {
                        Text("Stop Recording (${audioFrames.size} frames)")
                    }
                }

                RecordingState.Idle,
                RecordingState.Stopped -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                audioFrames = emptyList()
                                recordingSession.start(format)
                            }
                        }
                    ) {
                        Text("Start Recording")
                    }
                }
                is RecordingState.Error -> {
                    Column {
                        Text("Error: ${it.error.message}")
                        TextButton(onClick = {
                            scope.launch {
                                audioFrames = emptyList()
                                recordingSession.start(format)
                            }
                        }) {
                            Text("Start Recording")
                        }
                    }
                }
            }
        }
    }
}
