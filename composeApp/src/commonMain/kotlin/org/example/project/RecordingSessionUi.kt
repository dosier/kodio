package org.example.project

import AudioDataFlow
import AudioFormat
import RecordingSession
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import asAudioDataFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch


@Composable
fun RecordingSessionUi(
    recordingSession: RecordingSession,
    format: AudioFormat,
    onStopRecording: (AudioDataFlow) -> Unit = {}
) {
    val state by recordingSession.state.collectAsState()
    var audioFrames by remember {
        mutableStateOf(emptyList<ByteArray>())
    }
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
                            onStopRecording(audioFrames.asFlow().asAudioDataFlow(format))
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
