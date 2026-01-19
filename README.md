[![Maven Central](https://img.shields.io/maven-central/v/space.kodio/core?style=flat&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/space.kodio/core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat)](LICENSE)
[![Docs](https://img.shields.io/badge/Docs-GitHub%20Pages-4285F4?style=flat&logo=gitbook&logoColor=white)](https://dosier.github.io/kodio/)

![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-000000?style=flat&logo=apple&logoColor=white)
![macOS](https://img.shields.io/badge/macOS-000000?style=flat&logo=apple&logoColor=white)
![JVM](https://img.shields.io/badge/JVM-ED8B00?style=flat&logo=openjdk&logoColor=white)
![JavaScript](https://img.shields.io/badge/JS-F7DF1E?style=flat&logo=javascript&logoColor=black)
![Wasm](https://img.shields.io/badge/Wasm-654FF0?style=flat&logo=webassembly&logoColor=white)

> [!CAUTION]  
> This library is still in very early development.

# Kodio

**Kotlin Multiplatform Audio Library** — Recording, playback, and transcription with a modern coroutines-based API.

## Features

- **Simple Recording** — One-line recording with quality presets
- **Easy Playback** — Play recordings with a single method call
- **Multiplatform** — JVM, Android, iOS, macOS, JS, Wasm
- **Compose Integration** — Ready-to-use state holders and UI components
- **Live Waveforms** — Real-time amplitude data for visualizations
- **Permission Handling** — Built-in permission management
- **File I/O** — Save/load WAV files easily
- **Transcription** — Speech-to-text via OpenAI Whisper API

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
    implementation("space.kodio:core:0.1.1")
    
    // Optional: Compose state holders and waveform
    implementation("space.kodio:compose:0.1.1")
    
    // Optional: Material 3 UI components
    implementation("space.kodio:compose-material3:0.1.1")
    
    // Optional: Audio transcription (OpenAI Whisper)
    implementation("space.kodio:transcription:0.1.1")
}
```

## Documentation

📚 **[dosier.github.io/kodio](https://dosier.github.io/kodio/)**

- [Getting Started](https://dosier.github.io/kodio/getting-started.html)
- [Installation](https://dosier.github.io/kodio/installation.html)
- [Platform Setup](https://dosier.github.io/kodio/platform-setup.html)
- [Recording](https://dosier.github.io/kodio/recording.html)
- [Playback](https://dosier.github.io/kodio/playback.html)
- [Compose Integration](https://dosier.github.io/kodio/recorder-state.html)

## License

[Apache 2.0](LICENSE)

## Credits

Inspired by [kmp-record](https://github.com/theolm/kmp-record).
