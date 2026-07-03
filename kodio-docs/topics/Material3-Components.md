[//]: # (title: Material 3 Components)

<show-structure for="chapter" depth="2"/>
<primary-label ref="material3"/>

<tldr>
<p><b>Pre-built UI</b>: Material 3 buttons and dialogs for audio recording and playback, driven by <code>RecorderState</code> and <code>PlayerState</code>.</p>
</tldr>

The `compose-material3` module provides ready-to-use Material 3 components that wrap `RecorderState` and `PlayerState` from the `compose` module.

## Add the dependency {id="dependency"}

<tabs>
<tab title="Version Catalog">

```kotlin
implementation(libs.kodio.compose.material3)
```

</tab>
<tab title="Direct">

```kotlin
implementation("space.kodio.extensions:compose-material3:%kodio-version%")
```

</tab>
</tabs>

You also need the `compose` module for `rememberRecorderState()` and `rememberPlayerState()`.

## RecordAudioButton {id="record-button"}

A recording button with optional live waveform, permission handling, and error dialog:

```kotlin
val recorderState = rememberRecorderState()

RecordAudioButton(
    state = recorderState,
    showWaveform = true,
)
```

When `state.needsPermission` is true, the button shows `AudioPermissionButton` instead of the record toggle. When `state.error` is set, an `ErrorDialog` is shown automatically.

The toggle button shows:
- Microphone icon when idle
- Stop icon while recording
- Loading indicator while processing

## PlayAudioButton {id="play-button"}

A playback button for a specific `AudioRecording`:

```kotlin
val playerState = rememberPlayerState(recording)

PlayAudioButton(
    recording = recording,
    state = playerState,
)
```

The button shows:
- Play icon when ready or stopped
- Stop icon while playing (acts as pause toggle via `state.toggle()`)
- Loading indicator while loading
- A restart button when playing, paused, or finished

Errors are shown via `ErrorDialog` when `state.error` is set.

## AudioPermissionButton {id="permission-button"}

An icon button that reflects microphone permission state and requests access when needed:

```kotlin
val recorderState = rememberRecorderState()

AudioPermissionButton(state = recorderState)
```

This is used internally by `RecordAudioButton` when permission is required. You can also use it standalone in custom layouts.

## ErrorDialog {id="error-dialog"}

A Material 3 dialog for displaying an `AudioError`:

```kotlin
recorderState.error?.let { error ->
    ErrorDialog(
        error = error,
        onDismiss = { recorderState.clearError() }
    )
}
```

`RecordAudioButton` and `PlayAudioButton` show this dialog automatically when their state has an error.

## Complete example {id="complete"}

Full recording UI using shared state holders:

```kotlin
@Composable
fun MaterialAudioUI() {
    val recorderState = rememberRecorderState()
    val recording = recorderState.recording

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RecordAudioButton(state = recorderState)

        if (recording != null) {
            PlayAudioButton(
                recording = recording,
                state = rememberPlayerState(recording),
            )
        }
    }
}
```

## Component reference {id="reference"}

| Component | Signature | Purpose |
|-----------|-----------|---------|
| `RecordAudioButton` | `(modifier?, state?, showWaveform?)` | Recording toggle with optional waveform and permission flow |
| `PlayAudioButton` | `(recording, modifier?, state?, showWaveform?)` | Playback controls for one recording |
| `AudioPermissionButton` | `(state, modifier?)` | Permission request button |
| `ErrorDialog` | `(error, onDismiss)` | Display an `AudioError` |

<seealso style="cards">
    <category ref="compose">
        <a href="Recorder-State.md" summary="State holder for recording">RecorderState</a>
        <a href="Player-State.md" summary="State holder for playback">PlayerState</a>
        <a href="Audio-Waveform.md" summary="Waveform visualization">AudioWaveform</a>
    </category>
</seealso>
