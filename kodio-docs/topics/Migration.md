[//]: # (title: Migration)

<show-structure for="chapter" depth="2"/>

Guide for upgrading between Kodio versions.

## 0.1.x to 0.2.0 {id="0-1-x-to-0-2-0"}

### Compose API consolidation {id="0-2-0-compose"}

`RecordAudioState` / `rememberRecordAudioState()` and `PlayAudioState` / `rememberPlayAudioState()` were removed, along with `KodioTheme`, `KodioComponents`, `AudioGraph`, `KodioIcons`, and `KodioColors`. Use `RecorderState` / `PlayerState` and `AudioWaveform` instead.

<tabs>
<tab title="Before">

```kotlin
val recordState = rememberRecordAudioState()
KodioTheme {
    KodioComponents.RecordButton(state = recordState)
    AudioGraph(amplitudes = recordState.amplitudes)
}
```

</tab>
<tab title="After">

```kotlin
val recorderState = rememberRecorderState()

Button(
    onClick = { recorderState.toggle() },
    enabled = !recorderState.isProcessing,
) {
    Text(if (recorderState.isRecording) "Stop" else "Record")
}

if (recorderState.isRecording) {
    AudioWaveform(amplitudes = recorderState.liveAmplitudes)
}

recorderState.recording?.let { recording ->
    Button(onClick = { recording.play() }) {
        Text("Play Recording")
    }
}
```

</tab>
</tabs>

### Material 3 components {id="0-2-0-material3"}

`RecordAudioButton`, `PlayAudioButton`, `ErrorDialog`, and `AudioPermissionButton` now take `RecorderState` or `PlayerState` instead of the removed Compose state types.

<tabs>
<tab title="Before">

```kotlin
val recordState = rememberRecordAudioState()
RecordAudioButton(
    state = recordState,
    onRecordClick = { recordState.start() },
)
ErrorDialog(message = recordState.errorMessage, onDismiss = { recordState.clearError() })
```

</tab>
<tab title="After">

```kotlin
val recorderState = rememberRecorderState()

RecordAudioButton(state = recorderState)

recorderState.error?.let { error ->
    ErrorDialog(error = error, onDismiss = { recorderState.clearError() })
}

val playerState = rememberPlayerState(recording)
PlayAudioButton(recording = recording, state = playerState)
AudioPermissionButton(state = recorderState)
```

</tab>
</tabs>

> `RecordAudioButton` and `PlayAudioButton` show `ErrorDialog` automatically when `state.error` is set. You only need a standalone `ErrorDialog` for custom error UI.
>
{style="note"}

### Transcription configuration {id="0-2-0-transcription"}

`TranscriptionConfig` now has only the `language` field. Removed fields: `model`, `interimResults`, `punctuation`, `profanityFilter`, `diarization`, and `keywords`. Removed presets: `VoiceCommand` and `Meeting`. Removed type: `TranscriptionException`.

<tabs>
<tab title="Before">

```kotlin
audioFlow.transcribe(
    engine,
    TranscriptionConfig.VoiceCommand.copy(language = "en-US"),
)
```

</tab>
<tab title="After">

```kotlin
audioFlow.transcribe(
    engine,
    TranscriptionConfig(language = "en-US"),
    // or TranscriptionConfig.Default
)
```

</tab>
</tabs>

Errors from transcription now surface as `TranscriptionResult.Error` in the result flow, not as thrown `TranscriptionException`.

### JVM native path on macOS {id="0-2-0-jvm-macos"}

The macOS JVM native CoreAudio path now requires **JDK 22 or later** (Panama FFI finalized in JDK 22). On **JDK 24 and later**, pass `--enable-native-access=ALL-UNNAMED` to silence restricted native access warnings.

See [Platform Setup](Platform-Setup.md) for JVM flags and Gradle configuration.

### AudioRecording equality {id="0-2-0-recording-equals"}

`AudioRecording.equals()` now compares audio content (byte-for-byte), not just format and size. Two recordings with the same format and size but different samples are no longer equal. `hashCode()` remains coarse (format and size only), so do not use `AudioRecording` as a hash map key when content equality matters.

## 0.0.5 to 0.0.6 {id="0-0-5-to-0-0-6"}

Error types are now classes for proper stack traces:

<tabs>
<tab title="Before">

```kotlin
when (error) {
    AudioError.PermissionDenied -> ...
    AudioError.NotInitialized -> ...
}
```

</tab>
<tab title="After">

```kotlin
when (error) {
    is AudioError.PermissionDenied -> ...
    is AudioError.NotInitialized -> ...
}
```

</tab>
</tabs>

> Use `is` for pattern matching. Direct equality (`==`) no longer works.
>
{style="warning"}

## Session API to high-level API {id="session-to-high-level"}

<tabs>
<tab title="Session API">

```kotlin
val session = SystemAudioSystem.createRecordingSession()
session.start()
delay(5000)
session.stop()
val audioFlow = session.audioFlow.value!!
```

</tab>
<tab title="High-level API">

```kotlin
val recording = Kodio.record(duration = 5.seconds)
recording.play()
```

</tab>
</tabs>

## Compose migration {id="compose-migration"}

<tabs>
<tab title="Manual state">

```kotlin
var session by remember { ... }
var isRecording by remember { ... }
DisposableEffect(Unit) { ... }
```

</tab>
<tab title="RecorderState">

```kotlin
val recorderState = rememberRecorderState()
Button(onClick = { recorderState.toggle() }) { ... }
```

</tab>
</tabs>

## Accessing sessions {id="escape-hatch"}

The session API is still available for advanced use:

```kotlin
@Suppress("DEPRECATION")
val session = recorder.underlyingSession()
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md"/>
        <a href="Error-Handling.md"/>
    </category>
</seealso>
