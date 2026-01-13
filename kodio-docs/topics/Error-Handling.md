[//]: # (title: Error Handling)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Exhaustive error handling</b>: Use Kotlin's <code>when</code> expression with the sealed <code>AudioError</code> hierarchy.</p>
</tldr>

Kodio uses a sealed class hierarchy for errors, enabling exhaustive `when` expressions and pattern matching. This ensures you handle all possible error cases at compile time.

## Error types {id="error-types"}

All errors extend `AudioError`:

<deflist type="medium">
<def title="PermissionDenied">
User denied microphone access. Prompt them to enable it in settings.
</def>
<def title="DeviceNotFound">
No microphone or speaker available. Common on simulators or headless systems.
</def>
<def title="FormatNotSupported">
The requested audio format isn't supported on this platform.
</def>
<def title="DeviceError">
Hardware-level failure during recording or playback.
</def>
<def title="NotInitialized">
<b>Android only</b>: <code>Kodio.initialize(context)</code> wasn't called before recording.
</def>
<def title="AlreadyRecording">
Attempted to start recording when a recording is already in progress.
</def>
<def title="AlreadyPlaying">
Attempted to start playback when audio is already playing.
</def>
<def title="NoRecordingData">
Called <code>getRecording()</code> before any audio was recorded.
</def>
<def title="Unknown">
Unexpected error. Check the <code>cause</code> property for details.
</def>
</deflist>

## Handling errors {id="handling"}

Use try-catch with pattern matching:

```kotlin
try {
    val recording = Kodio.record(duration = 5.seconds)
} catch (e: AudioError) {
    when (e) {
        is AudioError.PermissionDenied -> {
            // Request permission
            Kodio.microphonePermission.request()
        }
        is AudioError.DeviceNotFound -> {
            showError("No microphone found")
        }
        is AudioError.NotInitialized -> {
            // Android: forgot to initialize
            showError("Call Kodio.initialize() first")
        }
        is AudioError.AlreadyRecording -> {
            // Ignore or stop current recording
        }
        else -> {
            showError(e.message ?: "Recording failed")
        }
    }
}
```

> Since `AudioError` is sealed, the compiler will warn you if you don't handle all cases (when not using `else`).
>
{style="tip"}

## In Compose {id="compose"}

`RecorderState` and `PlayerState` expose errors via the `error` property:

```kotlin
val recorderState = rememberRecorderState()

// Show error dialog
recorderState.error?.let { error ->
    AlertDialog(
        onDismissRequest = { recorderState.clearError() },
        title = { Text("Recording Error") },
        text = {
            Text(when (error) {
                is AudioError.PermissionDenied -> 
                    "Microphone access is required. Please enable it in settings."
                is AudioError.DeviceNotFound -> 
                    "No microphone detected."
                else -> 
                    error.message ?: "An error occurred"
            })
        },
        confirmButton = {
            TextButton(onClick = { recorderState.clearError() }) {
                Text("OK")
            }
        }
    )
}
```

> For a pre-built error dialog, use `ErrorDialog` from the [Material 3 Components](Material3-Components.md) module.
>
{style="tip"}

## Permission flow {id="permissions"}

The most common error is `PermissionDenied`. Here's a complete permission handling flow:

```kotlin
@Composable
fun RecorderWithPermissionFlow() {
    val recorderState = rememberRecorderState()
    var showSettings by remember { mutableStateOf(false) }

    when {
        recorderState.needsPermission -> {
            // First time: request permission
            PermissionPrompt(
                onRequest = { recorderState.requestPermission() }
            )
        }
        recorderState.error is AudioError.PermissionDenied -> {
            // Denied: suggest settings
            PermissionDeniedPrompt(
                onOpenSettings = { showSettings = true },
                onDismiss = { recorderState.clearError() }
            )
        }
        else -> {
            // Ready to record
            RecorderUI(recorderState)
        }
    }
}
```

## Error reference {id="reference"}

| Error | Cause | Solution |
|-------|-------|----------|
| `PermissionDenied` | User denied access | Request permission or guide to settings |
| `DeviceNotFound` | No audio hardware | Check device capabilities |
| `NotInitialized` | Missing init call | Call `Kodio.initialize(context)` on Android |
| `AlreadyRecording` | Concurrent recording | Stop current recording first |
| `AlreadyPlaying` | Concurrent playback | Stop current playback first |
| `FormatNotSupported` | Invalid format | Use supported format or preset |
| `NoRecordingData` | Empty recording | Record before calling `getRecording()` |
| `DeviceError` | Hardware failure | Retry or report to user |
| `Unknown` | Unexpected error | Log and report |

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Recording API">Recording</a>
        <a href="Playback.md" summary="Playback API">Playback</a>
    </category>
    <category ref="compose">
        <a href="Material3-Components.md" summary="ErrorDialog component">Material 3 Components</a>
    </category>
</seealso>
