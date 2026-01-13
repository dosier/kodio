[//]: # (title: RecorderState)

<show-structure for="chapter" depth="2"/>
<primary-label ref="compose"/>

<tldr>
<p><b>Compose state holder</b> for recording with automatic permission handling, live waveform data, and reactive UI updates.</p>
</tldr>

`RecorderState` is a Compose state holder that makes building recording UIs simple. It handles recording lifecycle, permission requests, error states, and provides live amplitude data for waveform visualizations.

## Basic usage {id="basic"}

Create a recording UI in just a few lines:

```kotlin
@Composable
fun VoiceRecorder() {
    val recorderState = rememberRecorderState()

    Button(onClick = { recorderState.toggle() }) {
        Text(if (recorderState.isRecording) "⏹ Stop" else "🎙 Record")
    }
}
```

The state automatically triggers recomposition when recording starts, stops, or errors occur.

## Configuration {id="config"}

Customize the recorder with quality presets and callbacks:

```kotlin
val recorderState = rememberRecorderState(
    quality = AudioQuality.High,
    onRecordingComplete = { recording ->
        // Called when recording stops
        viewModel.save(recording)
    }
)
```

## Waveform visualization {id="waveform"}

`RecorderState` provides `liveAmplitudes`—a list of normalized amplitude values (0.0 to 1.0) updated in real-time during recording:

```kotlin
if (recorderState.isRecording) {
    AudioWaveform(
        amplitudes = recorderState.liveAmplitudes,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    )
}
```

> See [AudioWaveform](Audio-Waveform.md) for customization options like colors, bar width, and gradients.
>
{style="tip"}

## Permission handling {id="permission"}

`RecorderState` tracks permission status and can request it:

```kotlin
@Composable
fun RecorderWithPermission() {
    val recorderState = rememberRecorderState()

    if (recorderState.needsPermission) {
        // Show permission request UI
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎤 Microphone access required")
            Button(onClick = { recorderState.requestPermission() }) {
                Text("Grant Access")
            }
        }
    } else {
        // Show recording UI
        RecordButton(recorderState)
    }
}
```

## Error handling {id="errors"}

Display errors when they occur:

```kotlin
recorderState.error?.let { error ->
    AlertDialog(
        onDismissRequest = { recorderState.clearError() },
        title = { Text("Recording Error") },
        text = { Text(error.message ?: "Unknown error") },
        confirmButton = {
            TextButton(onClick = { recorderState.clearError() }) {
                Text("OK")
            }
        }
    )
}
```

> For pre-built error UI, use `ErrorDialog` from the [Material 3 Components](Material3-Components.md) module.
>
{style="tip"}

## Complete example {id="complete"}

Here's a full recording UI with permission handling, waveform, and error display:

```kotlin
@Composable
fun CompleteRecorder() {
    val recorderState = rememberRecorderState(
        quality = AudioQuality.Voice,
        onRecordingComplete = { recording ->
            println("Recorded ${recording.calculatedDuration}")
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Permission check
        if (recorderState.needsPermission) {
            Text("🎤 Microphone access needed")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { recorderState.requestPermission() }) {
                Text("Grant Access")
            }
            return@Column
        }

        // Waveform
        if (recorderState.isRecording) {
            AudioWaveform(
                amplitudes = recorderState.liveAmplitudes,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                barColor = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
        }

        // Record button
        Button(
            onClick = { recorderState.toggle() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recorderState.isRecording) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (recorderState.isRecording) "⏹ Stop Recording" else "🎙 Start Recording")
        }

        // Error dialog
        recorderState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { recorderState.clearError() },
                title = { Text("Error") },
                text = { Text(error.message ?: "Recording failed") },
                confirmButton = {
                    TextButton(onClick = { recorderState.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
```

## API reference {id="api-reference"}

### Properties {id="properties" collapsible="true"}

<deflist type="medium">
<def title="isRecording: Boolean">Currently recording audio.</def>
<def title="isProcessing: Boolean">Processing after stop (encoding, etc.).</def>
<def title="recording: AudioRecording?">The completed recording, if available.</def>
<def title="hasRecording: Boolean"><code>true</code> if <code>recording</code> is not null.</def>
<def title="liveAmplitudes: List<Float>">Real-time amplitude values (0.0-1.0) for waveform display.</def>
<def title="error: AudioError?">Current error, if any.</def>
<def title="needsPermission: Boolean"><code>true</code> if microphone permission hasn't been granted.</def>
</deflist>

### Methods {id="methods" collapsible="true"}

<deflist type="medium">
<def title="start()">Start recording.</def>
<def title="stop()">Stop recording.</def>
<def title="toggle()">Start if stopped, stop if recording.</def>
<def title="reset()">Discard the current recording.</def>
<def title="requestPermission()">Request microphone permission from the OS.</def>
<def title="clearError()">Clear the current error state.</def>
</deflist>

<seealso style="cards">
    <category ref="compose">
        <a href="Player-State.md" summary="Compose state for playback">PlayerState</a>
        <a href="Audio-Waveform.md" summary="Waveform visualization">AudioWaveform</a>
        <a href="Material3-Components.md" summary="Pre-built UI components">Material 3 Components</a>
    </category>
</seealso>
