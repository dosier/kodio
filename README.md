[![Maven Central Version](https://img.shields.io/maven-central/v/space.kodio/core)](https://central.sonatype.com/artifact/space.kodio/core)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Docs](https://img.shields.io/badge/docs-GitHub%20Pages-blue)](https://dosier.github.io/kodio/)

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
- 🗣️ **Transcription** - Speech-to-text via OpenAI Whisper API

## Quick Start

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

## Installation

```kotlin
dependencies {
    // Core library (required)
    implementation("space.kodio:core:0.1.0")
    
    // Optional: Compose state holders and waveform
    implementation("space.kodio:compose:0.1.0")
    
    // Optional: Material 3 UI components
    implementation("space.kodio:compose-material3:0.1.0")
    
    // Optional: Audio transcription (OpenAI Whisper)
    implementation("space.kodio:transcription:0.1.0")
}
```

## Supported Platforms

| Platform | Status |
|----------|--------|
| 🤖 Android | ✅ Stable |
| 🍎 iOS | ✅ Stable |
| 🍏 macOS | ✅ Stable |
| ☕ JVM | ✅ Stable |
| 🌐 JS | ✅ Stable |
| 🔮 Wasm | ✅ Stable |

## Documentation

📚 **Full documentation:** [dosier.github.io/kodio](https://dosier.github.io/kodio/)

- [Getting Started](https://dosier.github.io/kodio/getting-started.html)
- [Installation](https://dosier.github.io/kodio/installation.html)
- [Platform Setup](https://dosier.github.io/kodio/platform-setup.html)
- [Recording](https://dosier.github.io/kodio/recording.html)
- [Playback](https://dosier.github.io/kodio/playback.html)
- [Compose Integration](https://dosier.github.io/kodio/recorder-state.html)

## License

See [LICENSE](LICENSE) file.

## Credits

This project is inspired by [kmp-record](https://github.com/theolm/kmp-record).
