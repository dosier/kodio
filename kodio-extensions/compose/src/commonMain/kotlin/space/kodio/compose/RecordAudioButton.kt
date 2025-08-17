package space.kodio.compose

import androidx.compose.runtime.Composable

@Composable
fun RecordAudioButton(
    state : RecordAudioState,
    theme: KodioTheme,
    components: KodioComponents
) {
    components.RecordingAudioButtonContent(
        state = state,
        colors = theme.colors(),
        icons = theme.icons(),
        graphTheme = theme.graphTheme()
    )
}

