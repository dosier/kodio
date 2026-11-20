[//]: # (title: Migration)

<show-structure for="chapter" depth="2"/>

Guide for upgrading between Kodio versions.

## 0.2.x to 0.3.0 {id="0-2-x-to-0-3-0"}

No source changes are required; these are behavior changes to be aware of.

### Playback {id="0-3-0-playback"}

- All built-in sessions use buffered transport playback with real seeking
  (`seekTo` returns `SeekResult.Applied`). `BaseAudioPlaybackSession.playBlocking`
  is deprecated for custom sessions.
- `State.Finished` is reported once the device has played the audio, not when
  the last bytes were handed to it. `awaitComplete()` followed by `release()`
  no longer cuts off the end of a clip.
- Calling `play()` again after `Finished` replays from the start (unless you
  seeked since).
- A failing source (e.g. a corrupt file) ends in `State.Error` after playing
  what was decoded, instead of staying `Playing`.
- `Player.release()` / `AudioPlaybackSession.release()` now also cancel the
  session's background work. Do not reuse a released player.

### Recording {id="0-3-0-recording"}

- `AudioRecording.duration` is computed from the PCM when no duration was
  supplied, so recordings from `Kodio.record` / `Recorder` are no longer `null`.
- Web: the browser's voice processing (echo cancellation, noise suppression,
  auto gain) stays on by default. Pass `inputProcessing = InputProcessing.Raw`
  to `Kodio.recorder` / `Kodio.record` / `rememberRecorderState` for the
  unprocessed signal.
- `Recorder.getRecording()` waits for audio a platform delivers just after
  `stop()` (new `AudioRecordingSession.awaitCaptureComplete()`), so web
  recordings keep their last ~40 ms.

### Errors {id="0-3-0-errors"}

`Kodio` helpers (`record`, `play`, `recorder`, `player`) rethrow
`CancellationException` instead of wrapping it in `AudioError`.

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

### Recording start failures {id="0-2-0-recording-start"}

In 0.2.0, `Recorder.start()` throws when the underlying recording session fails to start (for example a hardware error or an unsupported format that cannot be negotiated). Previously, some failures left the session in an error state without propagating to the caller. If you call `Recorder.start()` directly, wrap it in try/catch. In Compose, `RecorderState.start()` catches these exceptions and surfaces them via `RecorderState.error`.

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

## File I/O (0.2.x) {id="file-io-0-2-x"}

Container loaders no longer default to WAV. Format is detected from magic bytes, then extension. Unresolved formats throw `AudioFileReadError.UnsupportedFormat`.

Raw PCM construction was renamed:

```kotlin
// Before
AudioRecording.fromBytes(format, pcmData)

// After
AudioRecording.fromPcmBytes(format, pcmData)
```

Container load:

```kotlin
// Before (silent WAV default)
AudioRecording.fromBytes(fileBytes)

// After (auto-detect, optional fileName)
AudioRecording.fromBytes(fileBytes, fileName = "clip.mp3")
AudioRecording.fromBytes(fileBytes, AudioFileFormat.Wav)
```

MP3 decode is supported. MP3 encode throws `AudioFileWriteError.UnsupportedFormat`. See [File I/O](File-IO.md) and [Metadata](Metadata.md).

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md"/>
        <a href="File-IO.md"/>
        <a href="Error-Handling.md"/>
    </category>
</seealso>
