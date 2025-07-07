package org.example.project

import AudioFormat
import PlaybackSession
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


@Composable
fun PlaybackSessionUi(
    playbackSession: PlaybackSession,
    audioDataFlow: Flow<ByteArray>,
    audioFormat: AudioFormat,
) {
    val state by playbackSession.state.collectAsState()
    val scope = rememberCoroutineScope()
    Row {
        AnimatedContent(state) {
            when (it) {
                PlaybackState.Idle -> {
                    TextButton(onClick = {
                        scope.launch {
                            playbackSession.play(audioDataFlow, audioFormat)
                        }
                    }) {
                        Text("Start Playback")
                    }
                }

                PlaybackState.Finished -> {
                    TextButton(onClick = {
                        scope.launch {
                            playbackSession.play(audioDataFlow, audioFormat)
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
                                playbackSession.play(audioDataFlow, audioFormat)
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