[![Maven Central Version](https://img.shields.io/maven-central/v/space.kodio/core)](https://central.sonatype.com/artifact/space.kodio/core)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin)](http://kotlinlang.org)

> [!CAUTION]  
> This library is still in very early development.

# Kodio - Kotlin Multiplatform Audio Library

Kodio is a Kotlin Multiplatform library for straightforward audio recording and playback. It leverages coroutines and Flow to provide a modern, asynchronous API for handling audio streams across JVM, Android, Web (JS/Wasm), and iOS.

## Features

- 🎙️ **Simple Recording** - One-line recording with quality presets
- 🔊 **Easy Playback** - Play recordings with a single method call
- 📱 **Multiplatform** - JVM, Android, iOS, Web (JS/Wasm)
- 🎨 **Compose Integration** - Ready-to-use state holders and UI components
- 📊 **Live Waveforms** - Real-time amplitude data for visualizations
- 🔒 **Permission Handling** - Built-in permission management
- 💾 **File I/O** - Save/load WAV files easily

## Quick Start

### Simple Recording

```kotlin
import space.kodio.core.Kodio
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    // Record audio for 5 seconds
    val recording = Kodio.record(duration = 5.seconds)
    
    // Play it back
    recording.play()
    
    // Save to file
    recording.saveAs(Path("voice_note.wav"))
}
```

### Manual Recording Control

```kotlin
suspend fun manualRecording() {
    Kodio.record { recorder ->
        recorder.start()
        
        // Wait for user to stop...
        delay(userDuration)
        
        recorder.stop()
        
        // Access the recording
        val recording = recorder.getRecording()
        recording?.play()
    }
}
```

### Structured Concurrency with `use`

```kotlin
suspend fun recordWithAutoCleanup() {
    val recorder = Kodio.recorder(quality = AudioQuality.Voice)
    
    // Resources automatically released after use block
    recorder.use { r ->
        r.start()
        delay(3.seconds)
        r.stop()
        r.getRecording()?.saveAs(Path("memo.wav"))
    }
}
```

### Compose Integration

```kotlin
@Composable
fun VoiceRecorder() {
    val recorderState = rememberRecorderState(
        quality = AudioQuality.Standard,
        onRecordingComplete = { recording ->
            // Handle completed recording
        }
    )
    
    Column {
        // Status display
        Text(
            when {
                recorderState.isRecording -> "Recording..."
                recorderState.isProcessing -> "Processing..."
                recorderState.hasRecording -> "Recording ready"
                else -> "Ready to record"
            }
        )
        
        // Waveform visualization
        if (recorderState.isRecording) {
            AudioWaveform(amplitudes = recorderState.liveAmplitudes)
        }
        
        // Record button
        Button(
            onClick = { recorderState.toggle() },
            enabled = !recorderState.isProcessing
        ) {
            Text(if (recorderState.isRecording) "Stop" else "Record")
        }
        
        // Play the recording
        recorderState.recording?.let { recording ->
            val playerState = rememberPlayerState(
                recording = recording,
                onPlaybackComplete = { /* Handle completion */ }
            )
            
            Button(
                onClick = { playerState.toggle() },
                enabled = playerState.isReady
            ) {
                Text(
                    when {
                        playerState.isPlaying -> "Pause"
                        playerState.isPaused -> "Resume"
                        else -> "Play"
                    }
                )
            }
        }
        
        // Error handling
        recorderState.error?.let { error ->
            Text("Error: ${error.message}", color = Color.Red)
            Button(onClick = { recorderState.clearError() }) {
                Text("Dismiss")
            }
        }
    }
}
```

## Audio Quality Presets

Kodio provides simple quality presets for common use cases:

| Preset | Sample Rate | Channels | Bit Depth | Use Case |
|--------|-------------|----------|-----------|----------|
| `Voice` | 16 kHz | Mono | 16-bit | Speech, voice memos |
| `Standard` | 44.1 kHz | Mono | 16-bit | General audio |
| `High` | 48 kHz | Stereo | 16-bit | Professional audio |
| `Lossless` | 96 kHz | Stereo | 24-bit | Studio quality |

```kotlin
// Voice (mono, 16kHz) - great for speech
Kodio.record(duration = 5.seconds, quality = AudioQuality.Voice)

// Standard (mono, 44.1kHz) - balanced quality/size
Kodio.record(duration = 5.seconds, quality = AudioQuality.Standard)

// High (stereo, 48kHz) - professional quality
Kodio.record(duration = 5.seconds, quality = AudioQuality.High)

// Lossless (stereo, 96kHz) - studio quality
Kodio.record(duration = 5.seconds, quality = AudioQuality.Lossless)
```

## Installation

### Gradle

```kotlin
dependencies {
    // Core library
    implementation("space.kodio:core:0.1.0")
    
    // Optional: Compose extensions
    implementation("space.kodio:compose:0.1.0")
}
```

### Platform-Specific Setup

#### Android

1. **Manifest permission**: Add the `RECORD_AUDIO` permission to your `AndroidManifest.xml`:
    ```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    ```

2. **Initialization**: Initialize Kodio in your `Application` class:
    ```kotlin
    class App : Application() {
        override fun onCreate() {
            super.onCreate()
            Kodio.initialize(this)
        }
    }
    ```

3. **Runtime permissions**: Kodio handles permission requests automatically. Check permission state:
    ```kotlin
    val recorderState = rememberRecorderState()
    
    if (recorderState.needsPermission) {
        Button(onClick = { recorderState.requestPermission() }) {
            Text("Grant Microphone Access")
        }
    }
    ```

#### iOS

1. Add `NSMicrophoneUsageDescription` to your `Info.plist`:
    ```xml
    <key>NSMicrophoneUsageDescription</key>
    <string>This app needs microphone access to record audio.</string>
    ```

#### JVM (Desktop)

No special setup required. Works out of the box with available audio devices.

**System Properties:**

| Property | Default | Description |
|----------|---------|-------------|
| `kodio.useJavaSound` | `false` | Set to `true` to force using JavaSound (javax.sound.sampled) instead of native CoreAudio on macOS. Useful for debugging or if the native library has issues. |

Example:
```kotlin
fun main() {
    // Force JavaSound backend on macOS
    System.setProperty("kodio.useJavaSound", "true")
    
    // Now start your app...
}
```

Or via command line:
```bash
java -Dkodio.useJavaSound=true -jar your-app.jar
```

#### macOS

On macOS, Kodio uses native CoreAudio via Panama FFI for optimal audio quality. This requires:

1. **Microphone Permission**: The app (or Terminal/IDE if running in development) must have microphone access in **System Settings → Privacy & Security → Microphone**.

2. **Java 21+**: The native audio bridge uses Panama FFI which requires Java 21 or later.

> **Note**: If you're running from an IDE or Terminal and getting silent audio, make sure that app has microphone permission in macOS Privacy settings. macOS grants permissions per-app, not per-process.

## Error Handling

Kodio uses a sealed class hierarchy for errors:

```kotlin
when (val error = recorderState.error) {
    is AudioError.PermissionDenied -> {
        // Request permission or show explanation
    }
    is AudioError.DeviceNotFound -> {
        // Handle missing device: ${error.deviceId}
    }
    is AudioError.FormatNotSupported -> {
        // Fall back to different format
    }
    is AudioError.DeviceError -> {
        // Hardware issue: ${error.message}
    }
    is AudioError.NotInitialized -> {
        // Call Kodio.initialize() on Android
    }
    is AudioError.AlreadyRecording -> {
        // Stop current recording first
    }
    is AudioError.Unknown -> {
        // Check error.originalCause for details
    }
    null -> { /* No error */ }
}
```

## Advanced Usage

### Using the Low-Level API

For advanced use cases, you can access the underlying session APIs:

```kotlin
// Direct session access
val session = SystemAudioSystem.createRecordingSession()
session.start()
delay(5000)
session.stop()
val audioFlow = session.audioFlow.value

// Play back using the session API
val playback = SystemAudioSystem.createPlaybackSession()
audioFlow?.let { playback.setAudioFlow(it) }
playback.start()
```

### Custom Audio Formats

```kotlin
val customFormat = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Stereo,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
)

val recorder = Kodio.recorder {
    quality = AudioQuality.High
    device = specificMicrophone
}
```

### Device Selection

```kotlin
// List available devices
val inputs = Kodio.listInputDevices()
val outputs = Kodio.listOutputDevices()

// Record from specific device
val recorder = Kodio.recorder(device = inputs.firstOrNull())

// Play to specific device
Kodio.play(recording, device = outputs.firstOrNull())
```

## API Reference

### Core Classes

| Class | Description |
|-------|-------------|
| `Kodio` | Main entry point facade for recording and playback |
| `AudioRecording` | A completed audio recording with save/play methods |
| `Recorder` | Recording controller with start/stop/pause/resume |
| `Player` | Playback controller with load/play/pause/stop |
| `AudioQuality` | Preset quality configurations (Voice, Standard, High, Lossless) |
| `AudioError` | Sealed error hierarchy for all audio operations |
| `AudioSessionState` | Unified state enum (Idle, Active, Paused, Complete, Failed) |

### Compose State Holders

| Class | Description |
|-------|-------------|
| `RecorderState` | Reactive state holder with `isRecording`, `isProcessing`, `liveAmplitudes`, `error` |
| `PlayerState` | Reactive state holder with `isPlaying`, `isPaused`, `isReady`, `isFinished` |

### Compose Functions

| Function | Description |
|----------|-------------|
| `rememberRecorderState()` | Creates a remembered RecorderState with lifecycle handling |
| `rememberPlayerState()` | Creates a remembered PlayerState for playback control |
| `AudioWaveform()` | Waveform visualization component |

## Migration from 0.0.6

### Breaking Changes

1. **`AudioPermissionDeniedException`** is now a `class` instead of an `object`:
   ```kotlin
   // Old
   throw AudioPermissionDeniedException
   
   // New
   throw AudioPermissionDeniedException()
   throw AudioPermissionDeniedException("Custom message", cause)
   ```

2. **`AudioError` subtypes** are now classes instead of objects for proper exception semantics:
   ```kotlin
   // Old
   AudioError.PermissionDenied
   AudioError.NotInitialized
   
   // New
   AudioError.PermissionDenied()
   AudioError.NotInitialized()
   ```

### Migrating Recording Code

The new API is largely additive - existing code using `SystemAudioSystem` continues to work:

```kotlin
// Old API (still works)
val session = SystemAudioSystem.createRecordingSession()
session.start()
delay(5000)
session.stop()
val audioFlow = session.audioFlow.value!!
val playback = SystemAudioSystem.createPlaybackSession()
playback.setAudioFlow(audioFlow)
playback.start()

// New simplified API
val recording = Kodio.record(duration = 5.seconds)
recording.play()
```

### macOS Audio Improvements

The macOS native audio recording has been improved:
- Fixed buffer re-enqueue bug that caused choppy audio
- Increased buffer size from 20ms to 50ms for better headroom
- Increased buffer count from 3 to 5 for better resilience

## Testing

Run tests across all platforms:

```bash
./gradlew check
```

Run JVM tests only:

```bash
./gradlew :kodio-core:jvmTest
```

## License

See [LICENSE](LICENSE) file.

## Credits

This project is inspired by [kmp-record](https://github.com/theolm/kmp-record).
