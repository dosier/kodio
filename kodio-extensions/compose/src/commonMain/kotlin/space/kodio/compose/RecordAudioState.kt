package space.kodio.compose

import androidx.compose.runtime.*
import space.kodio.core.AudioDevice
import space.kodio.core.AudioFlow
import space.kodio.core.AudioRecordingSession
import space.kodio.core.SystemAudioSystem
import space.kodio.core.security.AudioPermissionManager

sealed class RecordAudioState {

    class NotReady(
        val permissionsState: AudioPermissionManager.State,
    ) : RecordAudioState()

    class Ready(
        val session: AudioRecordingSession,
        private val onSend: () -> Unit
    ) : RecordAudioState() {
        fun send() {
            onSend()
        }
    }

    class Error(
        val cause: Throwable
    ) :  RecordAudioState()
}

@Composable
public fun rememberRecordAudioState(
    preferredInput: AudioDevice.Input? = null,
    onFinishRecording: (AudioFlow) -> Unit = {},
): RecordAudioState {
    val permissionState by SystemAudioSystem.permissionManager.state.collectAsState()
    var state : RecordAudioState by remember { mutableStateOf(RecordAudioState.NotReady(permissionState)) }
    LaunchedEffect(permissionState, preferredInput) {
        val previousState = state
        state = if (permissionState == AudioPermissionManager.State.Granted) {
            runCatching { SystemAudioSystem.createRecordingSession(preferredInput) }
                .onSuccess { session ->
                    when(previousState) {
                        is RecordAudioState.Ready -> previousState.session.stop()
                        is RecordAudioState.NotReady -> {
                            when(previousState.permissionsState) {
                                AudioPermissionManager.State.Granted -> Unit
                                else -> session.start()
                            }
                        }
                        else -> Unit
                    }
                }
                .map {
                    RecordAudioState.Ready(
                        session = it,
                        onSend = {
                            val audioRecording = it.audioFlow.value
                            if (audioRecording != null)
                                onFinishRecording(audioRecording)
                        }
                    )
                }
                .getOrElse(RecordAudioState::Error)
        } else
            RecordAudioState.NotReady(permissionState)
    }
    return state
}