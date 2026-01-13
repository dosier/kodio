[//]: # (title: PlayerState)

<show-structure for="chapter" depth="2"/>
<primary-label ref="compose"/>

<tldr>
<p><b>Compose state holder</b> for audio playback with reactive UI updates and automatic resource management.</p>
</tldr>

`PlayerState` is a Compose state holder for audio playback. It tracks playback state, handles loading, and triggers recomposition when the player state changes.

## Basic usage {id="basic"}

Create a play button that reacts to state changes:

```kotlin
@Composable
fun AudioPlayer(recording: AudioRecording) {
    val playerState = rememberPlayerState(recording)

    Button(
        onClick = { playerState.toggle() },
        enabled = playerState.isReady
    ) {
        Text(
            when {
                playerState.isPlaying -> "⏸ Pause"
                playerState.isFinished -> "🔄 Replay"
                else -> "▶️ Play"
            }
        )
    }
}
```

## Load dynamically {id="dynamic"}

Load recordings after the composable is created:

```kotlin
@Composable
fun DynamicPlayer() {
    val playerState = rememberPlayerState()
    var recording by remember { mutableStateOf<AudioRecording?>(null) }

    // Load when recording changes
    LaunchedEffect(recording) {
        recording?.let { playerState.load(it) }
    }

    Column {
        // Some UI to select/create a recording...
        
        Button(
            onClick = { playerState.toggle() },
            enabled = playerState.isReady
        ) {
            Text(if (playerState.isPlaying) "Pause" else "Play")
        }
    }
}
```

## Record then play {id="record-play"}

Common pattern: record audio, then immediately enable playback:

```kotlin
@Composable
fun RecordAndPlay() {
    val recorderState = rememberRecorderState()
    val playerState = rememberPlayerState()

    // Auto-load recording into player
    LaunchedEffect(recorderState.recording) {
        recorderState.recording?.let {
            playerState.load(it)
        }
    }

    Column {
        // Record button
        Button(onClick = { recorderState.toggle() }) {
            Text(if (recorderState.isRecording) "⏹ Stop" else "🎙 Record")
        }

        // Play button (only when recording available)
        if (recorderState.hasRecording) {
            Button(
                onClick = { playerState.toggle() },
                enabled = playerState.isReady
            ) {
                Text(if (playerState.isPlaying) "⏸ Pause" else "▶️ Play")
            }
        }
    }
}
```

## Error handling {id="errors"}

Handle playback errors:

```kotlin
playerState.error?.let { error ->
    AlertDialog(
        onDismissRequest = { playerState.clearError() },
        title = { Text("Playback Error") },
        text = { Text(error.message ?: "Unable to play audio") },
        confirmButton = {
            TextButton(onClick = { playerState.clearError() }) {
                Text("OK")
            }
        }
    )
}
```

## Complete example {id="complete"}

A full playback UI with state indicators:

```kotlin
@Composable
fun CompletePlayer(recording: AudioRecording) {
    val playerState = rememberPlayerState(recording)

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator
        Text(
            text = when {
                !playerState.isReady -> "⏳ Loading..."
                playerState.isPlaying -> "🔊 Playing"
                playerState.isPaused -> "⏸️ Paused"
                playerState.isFinished -> "✅ Finished"
                else -> "⏹️ Ready"
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        // Control buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Play/Pause
            Button(
                onClick = { playerState.toggle() },
                enabled = playerState.isReady
            ) {
                Text(if (playerState.isPlaying) "⏸" else "▶️")
            }

            // Stop
            Button(
                onClick = { playerState.stop() },
                enabled = playerState.isPlaying || playerState.isPaused
            ) {
                Text("⏹")
            }
        }

        // Error display
        playerState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠️ ${error.message}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

## API reference {id="api-reference"}

### Properties {id="properties" collapsible="true"}

<deflist type="medium">
<def title="isPlaying: Boolean">Currently playing audio.</def>
<def title="isPaused: Boolean">Playback is paused (can resume).</def>
<def title="isReady: Boolean">Audio is loaded and ready to play.</def>
<def title="isFinished: Boolean">Playback has completed.</def>
<def title="recording: AudioRecording?">The currently loaded recording.</def>
<def title="error: AudioError?">Current error, if any.</def>
</deflist>

### Methods {id="methods" collapsible="true"}

<deflist type="medium">
<def title="load(recording)">Load an <code>AudioRecording</code> for playback.</def>
<def title="play()">Start or resume playback.</def>
<def title="pause()">Pause playback.</def>
<def title="stop()">Stop playback and reset to beginning.</def>
<def title="toggle()">Play if stopped/paused, pause if playing.</def>
<def title="clearError()">Clear the current error state.</def>
</deflist>

<seealso style="cards">
    <category ref="compose">
        <a href="Recorder-State.md" summary="State holder for recording">RecorderState</a>
        <a href="Audio-Waveform.md" summary="Waveform visualization">AudioWaveform</a>
        <a href="Material3-Components.md" summary="Pre-built UI components">Material 3 Components</a>
    </category>
</seealso>
