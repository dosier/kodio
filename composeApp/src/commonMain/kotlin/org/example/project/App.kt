package org.example.project

import AudioDataFlow
import AudioDevice
import AudioFormatSupport
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
        val recordingSession = remember(inputDevice) {
            inputDevice?.let(SystemAudioSystem::createRecordingSession)
        }
        val playbackSession = remember(outputDevice) {
            outputDevice?.let(SystemAudioSystem::createPlaybackSession)
        }
        var playbackAudioDataFlow by remember { mutableStateOf<AudioDataFlow?>(null) }
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
                AnimatedContent(recordingSession) { session ->
                    when {
                        session == null -> Text("Please select an input device")
                        else -> {
                            RecordingSessionUi(
                                recordingSession = session,
                                format = inputDevice?.formatSupport?.let {
                                    if (it is AudioFormatSupport.Known)
                                        it.defaultFormat
                                    else
                                        null
                                }?:AudioFormat.DEFAULT,
                                onStopRecording = { audioFrames ->
                                    playbackAudioDataFlow = audioFrames
                                }
                            )
                        }
                    }
                }
                AnimatedContent(playbackSession to playbackAudioDataFlow) { (session, flow) ->
                    when {
                        session == null -> Text("Please select an output device")
                        flow == null -> Text("Please start recording before playing back")
                        else -> {
                            PlaybackSessionUi(
                                playbackSession = session,
                                audioDataFlow = flow
                            )
                        }
                    }
                }
            }
        }
    }
}

