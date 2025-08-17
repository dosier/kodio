package space.kodio.compose

import androidx.compose.runtime.Composable

abstract class KodioComponents {

    @Composable
    abstract fun RecordingAudioButtonContent(
        state: RecordAudioState,
        colors: KodioColors,
        icons: KodioIcons,
        graphTheme: AudioGraphTheme
    )
}