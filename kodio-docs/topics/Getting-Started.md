[//]: # (title: Getting Started)

<show-structure for="chapter" depth="2"/>

<tldr>
<p><b>Minimum setup</b>: Add the dependency and start recording in 3 lines of code.</p>
</tldr>

Kodio is a Kotlin Multiplatform library for audio recording and playback. It provides a simple, coroutine-based API that works identically across all supported platforms.

<img src="kodio-architecture.svg" alt="Kodio Architecture" width="706" border-effect="rounded"/>

## Add the dependency {id="add-dependency"}

Add Kodio to your `commonMain` source set in your Gradle build file:

<tabs>
<tab title="Version Catalog" group-key="catalog">

First, add to your `libs.versions.toml`:

```toml
[versions]
kodio = "%kodio-version%"

[libraries]
kodio-core = { module = "space.kodio:core", version.ref = "kodio" }
```

Then in your `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kodio.core)
        }
    }
}
```

</tab>
<tab title="Direct" group-key="direct">

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("space.kodio:core:%kodio-version%")
        }
    }
}
```

</tab>
</tabs>

## Record and play audio {id="record-and-play"}

With Kodio, you can record audio and play it back with just a few lines of code. The API is designed to be intuitive and follows Kotlin idioms like coroutines and extension functions.

```kotlin
// Record 5 seconds of audio
val recording = Kodio.record(duration = 5.seconds)

// Play it back
recording.play()

// Save to a file
recording.saveAs(Path("audio.wav"))
```

The `record` function is a suspend function that captures audio for the specified duration and returns an `AudioRecording` object. You can then play it, save it, or process it further.

## Supported platforms {id="platforms"}

Kodio supports all major Kotlin Multiplatform targets:

| Platform | Status | Notes |
|----------|--------|-------|
| 🤖 Android | ✅ Stable | Requires `Kodio.initialize()` |
| 🍎 iOS | ✅ Stable | Requires Info.plist entry |
| 🍏 macOS | ✅ Stable | Requires entitlements |
| ☕ JVM | ✅ Stable | No setup required |
| 🌐 JS | ✅ Stable | HTTPS required |
| 🔮 Wasm | ✅ Stable | HTTPS required |

> For platform-specific setup instructions, see [Platform Setup](Platform-Setup.md).
> 
{style="tip"}

## Available modules {id="modules"}

Kodio is split into modules so you can include only what you need:

<deflist type="medium">
<def title="space.kodio:core">
The foundation module providing recording, playback, and file I/O. This is the only required module.
</def>
<def title="space.kodio:compose">
Compose Multiplatform state holders (<code>rememberRecorderState</code>, <code>rememberPlayerState</code>) and the <code>AudioWaveform</code> component.
</def>
<def title="space.kodio:compose-material3">
Pre-built Material 3 UI components like <code>RecordAudioButton</code>, <code>PlayAudioButton</code>, and <code>ErrorDialog</code>.
</def>
<def title="space.kodio:transcription">
Audio transcription using OpenAI Whisper API. Provides <code>OpenAIWhisperEngine</code> and the <code>AudioFlow.transcribe()</code> extension.
</def>
</deflist>

## What's next {id="next"}

<tabs>
<tab title="Quick patterns">

Jump to [Quick Start](Quick-Start.md) for common recording and playback patterns.

</tab>
<tab title="Full setup">

See [Installation](Installation.md) for detailed dependency configuration and [Platform Setup](Platform-Setup.md) for platform-specific requirements.

</tab>
<tab title="Compose UI">

If you're building with Compose, check out [RecorderState](Recorder-State.md) for reactive recording UI.

</tab>
</tabs>
