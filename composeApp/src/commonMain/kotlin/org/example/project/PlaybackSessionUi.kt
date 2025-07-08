package org.example.project

import AudioFlow
import AudioPlaybackSession
import AudioPlaybackState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


@Composable
fun PlaybackSessionUi(
    audioPlaybackSession: AudioPlaybackSession,
    audioDataFlow: AudioFlow,
) {
    val state by audioPlaybackSession.state.collectAsState()
    val scope = rememberCoroutineScope()
    Row {
        AnimatedContent(state) {
            when (it) {
                AudioPlaybackState.Idle -> {
                    TextButton(onClick = {
                        scope.launch {
                            audioPlaybackSession.play(audioDataFlow)
                        }
                    }) {
                        Text("Start Playback")
                    }
                }

                AudioPlaybackState.Finished -> {
                    TextButton(onClick = {
                        scope.launch {
                            audioPlaybackSession.play(audioDataFlow)
                        }
                    }) {
                        Text("Restart Playback")
                    }
                }

                AudioPlaybackState.Paused -> {
                    TextButton(onClick = audioPlaybackSession::resume) {
                        Text("Resume Playback")
                    }
                }

                AudioPlaybackState.Playing -> {
                    TextButton(onClick = audioPlaybackSession::pause) {
                        Text("Pause Playback")
                    }
                }

                is AudioPlaybackState.Error -> {
                    Column {
                        Text("Error: ${it.error.message}")
                        TextButton(onClick = {
                            scope.launch {
                                audioPlaybackSession.play(audioDataFlow)
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