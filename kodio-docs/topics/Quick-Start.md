[//]: # (title: Quick Start)

<show-structure for="chapter" depth="2"/>

<tldr>
<p><b>Copy-paste examples</b> for common audio recording and playback scenarios.</p>
</tldr>

This page provides ready-to-use code snippets for the most common Kodio use cases. For detailed explanations, see the dedicated pages for [Recording](Recording.md) and [Playback](Playback.md).

## Record for a fixed duration {id="timed-recording"}

Record audio for exactly 5 seconds and get the result:

```kotlin
val recording = Kodio.record(
    duration = 5.seconds,
    quality = AudioQuality.Voice
)
```

## Record until stopped {id="manual-recording"}

Start recording and wait for the user (or your app logic) to stop it:

```kotlin
val recording = Kodio.record { recorder ->
    recorder.start()
    // ... wait for user action ...
    recorder.stop()
    recorder.getRecording()
}
```

## Record with automatic cleanup {id="with-cleanup"}

Use Kotlin's `use` extension to ensure resources are released even if something goes wrong:

```kotlin
Kodio.recorder().use { recorder ->
    recorder.start()
    delay(5.seconds)
    recorder.stop()
    recorder.getRecording()?.saveAs(Path("audio.wav"))
}
```

## Play a recording {id="playback"}

<tabs>
<tab title="Simple">

One line to play audio and wait for completion:

```kotlin
recording.play()
```

</tab>
<tab title="With controls">

Get access to pause, resume, and other controls:

```kotlin
Kodio.play(recording) { player ->
    player.start()
    delay(2.seconds)
    player.pause()
    delay(1.seconds)
    player.resume()
    player.awaitComplete()
}
```

</tab>
</tabs>

## Save to file {id="save-file"}

Save a recording as a WAV file:

```kotlin
recording.saveAs(Path("my-recording.wav"))
```

## Compose UI {id="compose-ui"}

Build a recording UI with just a few lines:

```kotlin
@Composable
fun VoiceRecorder() {
    val recorderState = rememberRecorderState()
    
    Column {
        // Show waveform while recording
        if (recorderState.isRecording) {
            AudioWaveform(
                amplitudes = recorderState.liveAmplitudes,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            )
        }
        
        // Record button
        Button(onClick = { recorderState.toggle() }) {
            Text(if (recorderState.isRecording) "⏹ Stop" else "🎙 Record")
        }
    }
}
```

## Quality presets reference {id="quality-presets"}

Choose the right quality for your use case:

| Preset | Sample Rate | Channels | Best for |
|--------|-------------|----------|----------|
| 🎤 `Voice` | 16 kHz | Mono | Speech, voice memos |
| 🎵 `Standard` | 44.1 kHz | Mono | General audio |
| 🎧 `High` | 48 kHz | Stereo | Music, podcasts |
| 🎚️ `Lossless` | 96 kHz | Stereo, 24-bit | Studio recording |

```kotlin
Kodio.record(duration = 5.seconds, quality = AudioQuality.Voice)
Kodio.record(duration = 5.seconds, quality = AudioQuality.High)
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Deep dive into recording APIs">Recording</a>
        <a href="Playback.md" summary="Playback controls and options">Playback</a>
    </category>
    <category ref="compose">
        <a href="Recorder-State.md" summary="Compose state holder for recording">RecorderState</a>
    </category>
</seealso>
