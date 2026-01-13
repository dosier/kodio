[//]: # (title: Material 3 Components)

<show-structure for="chapter" depth="2"/>
<primary-label ref="material3"/>

<tldr>
<p><b>Pre-built UI</b>: Drop-in Material 3 buttons, dialogs, and indicators for audio recording and playback.</p>
</tldr>

The `compose-material3` module provides ready-to-use Material 3 components that work out of the box with `RecorderState` and `PlayerState`.

## Add the dependency {id="dependency"}

<tabs>
<tab title="Version Catalog">

```kotlin
implementation(libs.kodio.compose.material3)
```

</tab>
<tab title="Direct">

```kotlin
implementation("space.kodio:compose-material3:%kodio-version%")
```

</tab>
</tabs>

## RecordAudioButton {id="record-button"}

A styled recording button that shows the current state with appropriate icons and colors:

```kotlin
val recorderState = rememberRecorderState()

RecordAudioButton(
    isRecording = recorderState.isRecording,
    isProcessing = recorderState.isProcessing,
    onClick = { recorderState.toggle() }
)
```

The button automatically displays:
- 🎙️ **Microphone icon** when idle
- ⏹️ **Stop icon** while recording  
- ⏳ **Loading indicator** while processing

## PlayAudioButton {id="play-button"}

A playback button that adapts to the player's current state:

```kotlin
val playerState = rememberPlayerState(recording)

PlayAudioButton(
    isPlaying = playerState.isPlaying,
    isPaused = playerState.isPaused,
    isReady = playerState.isReady,
    isFinished = playerState.isFinished,
    onClick = { playerState.toggle() }
)
```

The button shows:
- ▶️ **Play icon** when ready or paused
- ⏸️ **Pause icon** while playing
- 🔄 **Replay icon** when finished

## AudioPermissionButton {id="permission-button"}

A button for requesting microphone permission with appropriate messaging:

```kotlin
val recorderState = rememberRecorderState()

if (recorderState.needsPermission) {
    AudioPermissionButton(
        onClick = { recorderState.requestPermission() }
    )
}
```

## ErrorDialog {id="error-dialog"}

A Material 3 dialog for displaying audio errors:

```kotlin
recorderState.error?.let { error ->
    ErrorDialog(
        error = error,
        onDismiss = { recorderState.clearError() }
    )
}
```

The dialog automatically formats error messages based on the error type:
- **PermissionDenied**: Suggests enabling microphone access
- **DeviceNotFound**: Indicates no microphone available
- **NotInitialized**: Reminds to call `Kodio.initialize()` on Android

## Complete example {id="complete"}

Here's a full recording UI using all Material 3 components:

```kotlin
@Composable
fun MaterialAudioUI() {
    val recorderState = rememberRecorderState()
    val playerState = rememberPlayerState()

    // Load recording into player when available
    LaunchedEffect(recorderState.recording) {
        recorderState.recording?.let { playerState.load(it) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission request
        if (recorderState.needsPermission) {
            AudioPermissionButton(
                onClick = { recorderState.requestPermission() }
            )
        } else {
            // Waveform during recording
            if (recorderState.isRecording) {
                AudioWaveform(
                    amplitudes = recorderState.liveAmplitudes,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }

            // Record button
            RecordAudioButton(
                isRecording = recorderState.isRecording,
                isProcessing = recorderState.isProcessing,
                onClick = { recorderState.toggle() }
            )

            // Play button (when recording available)
            if (recorderState.hasRecording) {
                PlayAudioButton(
                    isPlaying = playerState.isPlaying,
                    isPaused = playerState.isPaused,
                    isReady = playerState.isReady,
                    isFinished = playerState.isFinished,
                    onClick = { playerState.toggle() }
                )
            }
        }

        // Error handling
        recorderState.error?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { recorderState.clearError() }
            )
        }
    }
}
```

## Component reference {id="reference"}

| Component | Purpose | Required Props |
|-----------|---------|----------------|
| `RecordAudioButton` | Recording toggle | `isRecording`, `onClick` |
| `PlayAudioButton` | Playback toggle | `isPlaying`, `isReady`, `onClick` |
| `AudioPermissionButton` | Permission request | `onClick` |
| `ErrorDialog` | Error display | `error`, `onDismiss` |

<seealso style="cards">
    <category ref="compose">
        <a href="Recorder-State.md" summary="State holder for recording">RecorderState</a>
        <a href="Player-State.md" summary="State holder for playback">PlayerState</a>
        <a href="Audio-Waveform.md" summary="Waveform visualization">AudioWaveform</a>
    </category>
</seealso>
