package org.example.project

import AudioDevice
import PlaybackSession
import RecordingSession
import SystemAudioSystem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
        val scope = rememberCoroutineScope()
        Row {
            Column {
                Text("Input device: ${inputDevice?.name ?: "None"}")
                HorizontalDivider()
                InputDeviceListUi(onDeviceSelected = { inputDevice = it })
            }
            Column {
                Text("Output device: ${outputDevice?.name ?: "None"}")
                HorizontalDivider()
                OutputDeviceListUi(onDeviceSelected = { outputDevice = it })
            }
            TextButton(
                onClick = {
                    scope.launch {
                        recordingSession = SystemAudioSystem.createRecordingSession(inputDevice!!)
                    }
                },
                enabled = inputDevice != null
            ) {
                if (recordingSession == null) {
                    Text("Start Recording")
                } else {
                    Text("Stop Recording")
                }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        playbackSession = SystemAudioSystem.createPlaybackSession(outputDevice!!)
                    }
                },
                enabled = outputDevice != null
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