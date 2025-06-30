package org.example.project

import AudioDataFlow
import AudioDevice
import AudioFormat
import PlaybackSession
import RecordingSession
import RecordingState
import SystemAudioSystem
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import asAudioDataFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var inputDevice by remember { mutableStateOf<AudioDevice.Input?>(null) }
        var outputDevice by remember { mutableStateOf<AudioDevice.Output?>(null) }
        var recordingSession by remember { mutableStateOf<RecordingSession?>(null) }
        var playbackSession by remember { mutableStateOf<PlaybackSession?>(null) }
        var audioRecordingDataFlow by remember { mutableStateOf<AudioDataFlow?>(null) }
        Column {
            Column {
                Text("Input device: ${inputDevice?.name ?: "None"}")
                HorizontalDivider()
                InputDeviceListUi(onDeviceSelected = { inputDevice = it })
            }
            HorizontalDivider()
            Column {
                Text("Output device: ${outputDevice?.name ?: "None"}")
                HorizontalDivider()
                OutputDeviceListUi(onDeviceSelected = { outputDevice = it })
            }
            HorizontalDivider()

            AnimatedContent(recordingSession) {
                if (it == null) {
                    TextButton(
                        onClick = {
                            recordingSession = SystemAudioSystem.createRecordingSession(inputDevice!!)
                        },
                        enabled = inputDevice != null
                    ) {
                        Text("Create Recording Session")
                    }
                } else {
                    RecordingSessionUi(
                        recordingSession = it,
                        format = inputDevice?.defaultFormat!!,
                        onStopRecording = { audioFrames ->
                            audioRecordingDataFlow = audioFrames
                        }
                    )
                }
            }

            AnimatedContent(playbackSession) {
                if (it == null) {
                    TextButton(
                        onClick = {
                            playbackSession = SystemAudioSystem.createPlaybackSession(outputDevice!!)
                        },
                        enabled = outputDevice != null && audioRecordingDataFlow != null
                    ) {
                        Text("Create Playback Session")
                    }
                } else {
                    PlaybackSessionUi(
                        playbackSession = it,
                        audioDataFlow = audioRecordingDataFlow!!
                    )
                }
            }
        }
    }
}

@Composable
fun InputDeviceListUi(
    onDeviceSelected: (AudioDevice.Input) -> Unit
) {
    var devices by remember { mutableStateOf(listOf<AudioDevice.Input>()) }
    LaunchedEffect(true) {
        devices = SystemAudioSystem.listInputDevices()
    }
    Column {
        devices.forEach {
            TextButton(
                onClick = {
                    onDeviceSelected(it)
                }
            ) {
                Text(it.name)
            }
        }
    }
}

@Composable
fun OutputDeviceListUi(
    onDeviceSelected: (AudioDevice.Output) -> Unit
) {
    var devices by remember { mutableStateOf(listOf<AudioDevice.Output>()) }
    LaunchedEffect(true) {
        devices = SystemAudioSystem.listOutputDevices()
    }
    Column {
        devices.forEach {
            TextButton(
                onClick = {
                    onDeviceSelected(it)
                }
            ) {
                Text(it.name)
            }
        }
    }
}

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

@Composable
fun PlaybackSessionUi(
    playbackSession: PlaybackSession,
    audioDataFlow: AudioDataFlow
) {
    val state by playbackSession.state.collectAsState()
    val scope = rememberCoroutineScope()
    Row {
        AnimatedContent(state) {
            when (it) {
                PlaybackState.Idle -> {
                    TextButton(onClick = {
                        scope.launch {
                            playbackSession.play(audioDataFlow)
                        }
                    }) {
                        Text("Start Playback")
                    }
                }

                PlaybackState.Finished -> {
                    TextButton(onClick = {
                        scope.launch {
                            playbackSession.play(audioDataFlow)
                        }
                    }) {
                        Text("Restart Playback")
                    }
                }

                PlaybackState.Paused -> {
                    TextButton(onClick = playbackSession::resume) {
                        Text("Resume Playback")
                    }
                }

                PlaybackState.Playing -> {
                    TextButton(onClick = playbackSession::pause) {
                        Text("Pause Playback")
                    }
                }

                is PlaybackState.Error -> {
                    Column {
                        Text("Error: ${it.error.message}")
                        TextButton(onClick = {
                            scope.launch {
                                playbackSession.play(audioDataFlow)
                            }
                        }) {
                            Text("Restart Playback")
                        }
                    }
                }
            }
        }
    }
}