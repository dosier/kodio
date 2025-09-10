package space.kodio.compose.material3

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import space.kodio.compose.*
import space.kodio.compose.material3.component.AudioPermissionButton
import space.kodio.compose.material3.component.AudioPlaybackSessionButton
import space.kodio.compose.material3.component.AudioRecordingSessionButton
import space.kodio.compose.material3.component.ErrorDialog

object KodioComponentMaterial3 : KodioComponents()  {

    @Composable
    override fun RecordingAudioButtonContent(
        state: RecordAudioState,
        colors: KodioColors,
        icons: KodioIcons,
        graphTheme: AudioGraphTheme
    ) {
        Surface(
            modifier = Modifier.semantics { role = Role.Button },
            shape = ButtonDefaults.shape,
            color = colors.buttonContainerColor,
            contentColor = colors.buttonContentColor
        ) {
            AnimatedContent(state) {
                when(val state = it) {
                    is RecordAudioState.Error -> {
                        ErrorDialog(error = state.cause)
                    }
                    is RecordAudioState.NotReady -> {
                        AudioPermissionButton(
                            permissionState = state.permissionsState,
                            icons = icons
                        )
                    }
                    is RecordAudioState.Ready -> {
                        AudioRecordingSessionButton(
                            session = state.session,
                            icons = icons,
                            colors = colors,
                            graphTheme = graphTheme,
                            onSend = state::send
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun PlayAudioButtonContent(
        state: PlayAudioState,
        colors: KodioColors,
        icons: KodioIcons,
        graphTheme: AudioGraphTheme
    ) {
        Surface(
            modifier = Modifier.semantics { role = Role.Button },
            shape = ButtonDefaults.shape,
            color = colors.buttonContainerColor,
            contentColor = colors.buttonContentColor
        ) {
            AnimatedContent(state) {
                when(val state = it) {
                    is PlayAudioState.Error -> {
                        ErrorDialog(error = state.cause)
                    }
                    PlayAudioState.NotReady -> {
                        CircularProgressIndicator()
                    }
                    is PlayAudioState.Ready -> {
                        AudioPlaybackSessionButton(
                            session = state.session,
                            icons = icons,
                            colors = colors,
                            graphTheme = graphTheme,
                        )
                    }
                }
            }
        }
    }
}