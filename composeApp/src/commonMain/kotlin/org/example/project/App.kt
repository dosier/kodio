package org.example.project

import AudioDevice
import AudioFormat
import PlaybackSession
import RecordingSession
import SystemAudioSystem
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
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
        var recordedAudioFrames by remember { mutableStateOf(emptyList<ByteArray>()) }
        val scope = rememberCoroutineScope()
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
                            scope.launch {
                                recordingSession?.stop()
                                inputDevice?.let {
                                    recordingSession = SystemAudioSystem.createRecordingSession(it)
                                }
                            }
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
                            recordedAudioFrames = audioFrames
                        }
                    )
                }
            }

            TextButton(
                onClick = {
                    playbackSession?.stop()
                    scope.launch {
                        playbackSession = SystemAudioSystem.createPlaybackSession(outputDevice!!)
                        playbackSession?.play(recordedAudioFrames.asFlow(), outputDevice!!.defaultFormat)
                    }
                },
                enabled = outputDevice != null && recordedAudioFrames.isNotEmpty()
            ) {
                if (playbackSession == null) {
                    Text("Start Playback")
                } else {
                    Text("Stop Playback")
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
    onStopRecording: (List<ByteArray>) -> Unit = {}
) {
    val state by recordingSession.state.collectAsState()
    var audioFrames by remember {
        mutableStateOf(emptyList<ByteArray>())
    }
    val scope = rememberCoroutineScope()
    scope.launch {
        recordingSession.audioDataFlow.collect {
            audioFrames = audioFrames + it
        }
    }
    Row {

        AnimatedContent(state) {
            when (it) {
                RecordingState.RECORDING -> {
                    TextButton(
                        onClick = {
                            recordingSession.stop()
                            onStopRecording(audioFrames)
                        }
                    ) {
                        Text("Stop Recording (${audioFrames.size} frames)")
                    }
                }
                RecordingState.IDLE,
                RecordingState.STOPPED ->  {
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
                RecordingState.ERROR -> Text("Error")
            }
        }
    }
}