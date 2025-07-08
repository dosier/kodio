package org.example.project

import AudioDevice
import AudioFlow
import AudioPlaybackSession
import AudioRecordingSession
import SystemAudioSystem
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var inputDevice by remember { mutableStateOf<AudioDevice.Input?>(null) }
        var outputDevice by remember { mutableStateOf<AudioDevice.Output?>(null) }
        var audioRecordingSession : AudioRecordingSession? by remember { mutableStateOf(null)}
        var audioPlaybackSession : AudioPlaybackSession? by remember { mutableStateOf(null)}
        var error : Throwable? by remember { mutableStateOf(null)}
        var recordedAudioFlow by remember { mutableStateOf<AudioFlow?>(null) }

        LaunchedEffect(inputDevice) {
            runCatching {
                audioRecordingSession = inputDevice?.let {
                    SystemAudioSystem
                        .createRecordingSession(it)
                        .also { error = null }
                }
            }.onFailure {
                error = it
                audioRecordingSession = null
                inputDevice = null
            }
        }
        LaunchedEffect(outputDevice) {
            runCatching {
                audioPlaybackSession = outputDevice?.let {
                    SystemAudioSystem
                        .createPlaybackSession(it)
                        .also { error = null }
                }
            }.onFailure {
                error = it
                audioPlaybackSession = null
                outputDevice = null
            }
        }
        LaunchedEffect(audioRecordingSession) {
            audioRecordingSession?.audioFlow?.collect {
                recordedAudioFlow = it
            }
        }
        Column(Modifier.safeContentPadding()) {
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
            Column {
                AnimatedContent(audioRecordingSession) { session ->
                    when {
                        session == null -> Text("Please select an input device")
                        else -> RecordingSessionUi(audioRecordingSession = session)
                    }
                }
                AnimatedContent(audioPlaybackSession to recordedAudioFlow) { (playback, audioFlow) ->
                    when {
                        playback == null -> Text("Please select an output device")
                        audioFlow == null -> Text("Please start recording before playing back")
                        else -> {
                            @Suppress("UNCHECKED_CAST")
                            PlaybackSessionUi(
                                audioPlaybackSession = playback,
                                audioDataFlow = audioFlow,
                            )
                        }
                    }
                }
            }
            if (error != null)
                Text("Error: ${error?.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

