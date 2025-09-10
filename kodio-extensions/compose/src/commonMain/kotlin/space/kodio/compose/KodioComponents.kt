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

    @Composable
    abstract fun PlayAudioButtonContent(
        state: PlayAudioState,
        colors: KodioColors,
        icons: KodioIcons,
        graphTheme: AudioGraphTheme
    )
}