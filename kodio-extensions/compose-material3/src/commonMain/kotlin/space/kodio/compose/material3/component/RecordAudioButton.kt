package space.kodio.compose.material3.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
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
import space.kodio.compose.*
import space.kodio.compose.material3.KodioComponentMaterial3
import space.kodio.compose.material3.KodioThemeMaterial3
import space.kodio.core.AudioDevice
import space.kodio.core.AudioFlow
import space.kodio.core.AudioRecordingSession

@Composable
fun RecordAudioButton(preferredInput: AudioDevice.Input, ) {
    RecordAudioButton(state = rememberRecordAudioState(preferredInput))
}

@Composable
fun RecordAudioButton(state : RecordAudioState = rememberRecordAudioState()) {
    RecordAudioButton(
        state = state,
        theme = KodioThemeMaterial3,
        components = KodioComponentMaterial3
    )
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioRecordingSessionButton(
    session: AudioRecordingSession,
    icons: KodioIcons,
    colors: KodioColors,
    graphTheme: AudioGraphTheme,
    onSend: suspend () -> Unit,
    errorDialog: @Composable ((Throwable) -> Unit)? = null,
) {
    val state by session.state.collectAsState()
    Row(modifier = Modifier.animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            state = state,
            icons = icons,
            onStart = session::start,
            onStop = session::reset,
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
                    .padding(
                        top = 4.dp,
                        bottom = 4.dp,
//                        end = if (reverseDirection) 0.dp else 24.dp,
//                        start = if (reverseDirection) 24.dp else 0.dp,
                    )
                    .clip(shape = MaterialTheme.shapes.large),
            )
        }
        ActionButton2(
            state = state,
            icons = icons,
            onSend = {
                session.stop()
                onSend()
                session.reset()
            }
        )
        (state as? AudioRecordingSession.State.Error)?.error?.also { error ->
            errorDialog?.invoke(error)
        }
    }
}
@Composable
private fun ActionButton(
    state: AudioRecordingSession.State,
    icons: KodioIcons,
    onStart: suspend () -> Unit,
    onStop: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (state) {
        is AudioRecordingSession.State.Error -> {
            IconButton(onClick = { scope.launch { onStart() } }) {
                Icon(icons.retryIcon, contentDescription = "Re-try")
            }
        }
        is AudioRecordingSession.State.Idle -> {
            IconButton(onClick = { scope.launch { onStart() } }) {
                Icon(icons.micIcon, contentDescription = "Record")
            }
        }
        is AudioRecordingSession.State.Recording -> {
            IconButton(onClick = { scope.launch { onStop() } }) {
                Icon(icons.discardIcon, contentDescription = "Discord")
            }
        }
        is AudioRecordingSession.State.Stopped -> Unit
    }
}

@Composable
private fun ActionButton2(
    state: AudioRecordingSession.State,
    icons: KodioIcons,
    onSend: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (state) {
        is AudioRecordingSession.State.Recording -> {
            IconButton(onClick = { scope.launch { onSend() } }) {
                Icon(icons.checkIcon, contentDescription = "Send")
            }
        }
        else -> Unit
    }
}