package space.kodio.compose.material3.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.kodio.compose.AudioGraph
import space.kodio.compose.AudioGraphTheme
import space.kodio.compose.KodioColors
import space.kodio.compose.KodioIcons
import space.kodio.compose.PlayAudioButton
import space.kodio.compose.PlayAudioState
import space.kodio.compose.material3.KodioComponentMaterial3
import space.kodio.compose.material3.KodioThemeMaterial3
import space.kodio.compose.rememberPlayAudioState
import space.kodio.core.AudioDevice
import space.kodio.core.AudioFlow
import space.kodio.core.AudioPlaybackSession

@Composable
fun PlayAudioButton(preferredOutput: AudioDevice.Output) =
    PlayAudioButton(state = rememberPlayAudioState(preferredOutput))

@Composable
fun PlayAudioButton(preferredOutput: AudioDevice.Output, audioFlow: AudioFlow) =
    PlayAudioButton(state = rememberPlayAudioState(preferredOutput = preferredOutput, audioFlow = audioFlow))

@Composable
fun PlayAudioButton(audioFlow: AudioFlow) =
    PlayAudioButton(state = rememberPlayAudioState(audioFlow = audioFlow))

@Composable
fun PlayAudioButton(state : PlayAudioState = rememberPlayAudioState()) {
    PlayAudioButton(
        state = state,
        theme = KodioThemeMaterial3,
        components = KodioComponentMaterial3
    )
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioPlaybackSessionButton(
    session: AudioPlaybackSession,
    icons: KodioIcons,
    colors: KodioColors,
    graphTheme: AudioGraphTheme,
    errorDialog: @Composable ((Throwable) -> Unit)? = null,
) {
    val state by session.state.collectAsState()
    Row(modifier = Modifier.animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            state = state,
            icons = icons,
            onStart = session::play,
            onResume = session::resume,
            onPause = session::pause,
        )
        val flow: AudioFlow? by session.audioFlow.collectAsState()
        flow?.let {
            AudioGraph(
                flow = it,
                theme = graphTheme,
                modifier = Modifier
                    .height(40.dp)
                    .width(120.dp)
                    .heightIn(min = ButtonDefaults.MinHeight)
                    .widthIn(min = ButtonDefaults.MinWidth)
                    .padding(top = 4.dp, bottom = 4.dp,)
                    .clip(shape = MaterialTheme.shapes.large),
            )
        }
        ActionButton2(
            state = state,
            icons = icons,
            replay = {
                session.stop()
                session.play()
            }
        )
        (state as? AudioPlaybackSession.State.Error)?.error?.also { error ->
            errorDialog?.invoke(error)
        }
    }
}

@Composable
private fun ActionButton(
    state: AudioPlaybackSession.State,
    icons: KodioIcons,
    onStart: suspend () -> Unit,
    onResume: suspend () -> Unit,
    onPause: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    when (state) {
        is AudioPlaybackSession.State.Error -> {
            IconButton(onClick = { scope.launch { onStart() } }) {
                Icon(icons.retryIcon, contentDescription = "Re-try")
            }
        }
        is AudioPlaybackSession.State.Finished,
        is AudioPlaybackSession.State.Ready -> {
            IconButton(onClick = { scope.launch { onStart() } }) {
                Icon(icons.playIcon, contentDescription = "Idle")
            }
        }
        is AudioPlaybackSession.State.Playing -> {
            IconButton(onClick = { scope.launch { onPause() } }) {
                Icon(icons.stopIcon, contentDescription = "Pause")
            }
        }
        AudioPlaybackSession.State.Idle -> Unit
        AudioPlaybackSession.State.Paused -> {
            IconButton(onClick = { scope.launch { onResume() } }) {
                Icon(icons.playIcon, contentDescription = "Resume")
            }
        }
    }
}

@Composable
private fun ActionButton2(
    state: AudioPlaybackSession.State,
    icons: KodioIcons,
    replay: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (state) {
        is AudioPlaybackSession.State.Paused,
        is AudioPlaybackSession.State.Playing -> {
            IconButton(onClick = { scope.launch { replay() } }) {
                Icon(icons.retryIcon, contentDescription = "Replay")
            }
        }
        else -> Unit
    }
}