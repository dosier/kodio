[![Maven Central](https://img.shields.io/maven-central/v/space.kodio/core?style=flat&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/space.kodio/core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
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

**Kotlin Multiplatform Audio Library** for recording, playback, and transcription with a coroutines-based API.

## Features

- **Recording**: One-line recording with quality presets
- **Playback**: Play recordings with a single method call
- **Multiplatform**: JVM, Android, iOS, macOS, JS, Wasm
- **Compose Integration**: State holders and UI components
- **Live Waveforms**: Real-time amplitude data for visualizations
- **Permission Handling**: Built-in permission management
- **File I/O**: Save and load WAV files
- **Transcription**: Speech-to-text via OpenAI Whisper API
- **Ktor Extension**: WebSocket streaming of `AudioFlow`

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
    implementation("space.kodio:core:0.1.5")
    
    // Optional: Compose state holders and waveform
    implementation("space.kodio.extensions:compose:0.1.5")
    
    // Optional: Material 3 UI components
    implementation("space.kodio.extensions:compose-material3:0.1.5")
    
    // Optional: Audio transcription (OpenAI Whisper)
    implementation("space.kodio.extensions:transcription:0.1.5")
    
    // Optional: Ktor WebSocket streaming of AudioFlow
    implementation("space.kodio.extensions:ktor:0.1.5")
}
```

## Platform Notes

The JVM target runs on Linux, macOS, and Windows via JavaSound. On macOS, an optional native CoreAudio path is available. On Windows, only the JavaSound path is supported; native macOS libraries are not bundled for Windows hosts.

## Logging

Kodio is **silent by default**: no log output is produced until you configure it. Consumers decide when and where logs appear, rather than Kodio printing to the console unprompted.

Enable logging at application startup with the built-in platform console writer:

```kotlin
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

Kodio.configureLogging {
    minLevel = LogLevel.Debug
    addWriter(platformLogWriter())
}
```

`platformLogWriter()` routes to Logcat on Android, NSLog on Apple platforms, the browser console on JS/Wasm, and stdout/stderr on JVM. The same API works across all targets.

To stay silent (the default), do nothing, or explicitly disable:

```kotlin
Kodio.configureLogging { minLevel = LogLevel.None }
```

Bridge to your own logging backend (Kermit, SLF4J, Crashlytics, etc.) by implementing `KodioLogWriter`:

```kotlin
Kodio.configureLogging {
    minLevel = LogLevel.Warn
    addWriter { level, tag, message, throwable ->
        MyLogger.log(level.name, tag, message, throwable)
    }
}
```

**Log levels:** `Trace`, `Debug`, `Info`, `Warn`, `Error`, `None`. Messages at or above `minLevel` are forwarded to all registered writers.

See the [Logging](https://dosier.github.io/kodio/logging.html) docs page for full details.

## Documentation

**[dosier.github.io/kodio](https://dosier.github.io/kodio/)**

- [Getting Started](https://dosier.github.io/kodio/getting-started.html)
- [Installation](https://dosier.github.io/kodio/installation.html)
- [Platform Setup](https://dosier.github.io/kodio/platform-setup.html)
- [Recording](https://dosier.github.io/kodio/recording.html)
- [Playback](https://dosier.github.io/kodio/playback.html)
- [Compose Integration](https://dosier.github.io/kodio/recorder-state.html)
- [Ktor Extension](https://dosier.github.io/kodio/ktor-extension.html)
- [Logging](https://dosier.github.io/kodio/logging.html)

## License

[Apache 2.0](LICENSE)

## Credits

Inspired by [kmp-record](https://github.com/theolm/kmp-record).
