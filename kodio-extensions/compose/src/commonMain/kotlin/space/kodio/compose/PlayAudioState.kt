package space.kodio.compose

import androidx.compose.runtime.*
import space.kodio.core.AudioDevice
import space.kodio.core.AudioFlow
import space.kodio.core.AudioPlaybackSession
import space.kodio.core.SystemAudioSystem

sealed class PlayAudioState {

    data object NotReady : PlayAudioState()

    data class Ready(
        val session: AudioPlaybackSession,
        private val onFinished: () -> Unit
    ) : PlayAudioState()

    class Error(
        val cause: Throwable
    ) : PlayAudioState()
}

@Composable
public fun rememberPlayAudioState(
    preferredOutput: AudioDevice.Output? = null,
    audioFlow: AudioFlow? = null,
    onFinishedPlayback: () -> Unit = {},
) : PlayAudioState {
    var state : PlayAudioState by remember { mutableStateOf(PlayAudioState.NotReady) }
    LaunchedEffect(preferredOutput, audioFlow) {
        val previousState = state
        state = runCatching {
            SystemAudioSystem.createPlaybackSession(preferredOutput)
        }.onSuccess { session ->
            when(previousState) {
                is PlayAudioState.Ready -> previousState.session.stop()
                else -> Unit
            }
            if (audioFlow != null)
                session.load(audioFlow)
        }.map {
            PlayAudioState.Ready(
                session = it,
                onFinished = onFinishedPlayback
            )
        }.getOrElse {
            PlayAudioState.Error(it)
        }
    }
    return state
}