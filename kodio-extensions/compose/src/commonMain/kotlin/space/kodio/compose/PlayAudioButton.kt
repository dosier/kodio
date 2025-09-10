package space.kodio.compose

import androidx.compose.runtime.Composable

@Composable
fun PlayAudioButton(
    state : PlayAudioState,
    theme: KodioTheme,
    components: KodioComponents
) {
    components.PlayAudioButtonContent(
        state = state,
        colors = theme.colors(),
        icons = theme.icons(),
        graphTheme = theme.graphTheme()
    )
}