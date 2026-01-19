[//]: # (title: Installation)

<show-structure for="chapter" depth="2"/>

<tldr>
<p><b>Requirements</b>: Kotlin %kotlin-version%+, Gradle 8.0+</p>
</tldr>

This guide covers adding Kodio to your Kotlin Multiplatform project.

## Requirements {id="requirements"}

<deflist type="narrow">
<def title="Kotlin">%kotlin-version% or later</def>
<def title="Gradle">8.0 or later</def>
<def title="Android">minSdk 24 (Android 7.0)</def>
<def title="iOS">13.0 or later</def>
</deflist>

## Add the dependency {id="gradle-setup"}

<tabs>
<tab title="Version Catalog" group-key="catalog">

<procedure title="Using Gradle Version Catalog" id="version-catalog-setup">
<step>

Add the version and library to your `gradle/libs.versions.toml`:

```toml
[versions]
kodio = "%kodio-version%"

[libraries]
kodio-core = { module = "space.kodio:core", version.ref = "kodio" }
kodio-compose = { module = "space.kodio:compose", version.ref = "kodio" }
kodio-compose-material3 = { module = "space.kodio:compose-material3", version.ref = "kodio" }
kodio-transcription = { module = "space.kodio:transcription", version.ref = "kodio" }
```

</step>
<step>

Add the dependency to your `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kodio.core)
        }
    }
}
```

</step>
</procedure>

</tab>
<tab title="Direct" group-key="direct">

Add directly to your `build.gradle.kts`:

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

## Optional modules {id="optional-modules"}

Kodio provides additional modules for Compose integration:

<tabs>
<tab title="Version Catalog" group-key="catalog">

```kotlin
commonMain.dependencies {
    // Core (required)
    implementation(libs.kodio.core)
    
    // Compose state holders + AudioWaveform
    implementation(libs.kodio.compose)
    
    // Material 3 UI components
    implementation(libs.kodio.compose.material3)
    
    // Audio transcription (OpenAI Whisper)
    implementation(libs.kodio.transcription)
}
```

</tab>
<tab title="Direct" group-key="direct">

```kotlin
commonMain.dependencies {
    // Core (required)
    implementation("space.kodio:core:%kodio-version%")
    
    // Compose state holders + AudioWaveform
    implementation("space.kodio:compose:%kodio-version%")
    
    // Material 3 UI components
    implementation("space.kodio:compose-material3:%kodio-version%")
    
    // Audio transcription (OpenAI Whisper)
    implementation("space.kodio:transcription:%kodio-version%")
}
```

</tab>
</tabs>

### Module overview {id="module-overview"}

| Module | Contents | When to use |
|--------|----------|-------------|
| `core` | Recording, playback, file I/O | Always required |
| `compose` | `rememberRecorderState`, `rememberPlayerState`, `AudioWaveform` | Compose Multiplatform apps |
| `compose-material3` | `RecordAudioButton`, `PlayAudioButton`, `ErrorDialog` | Quick Material 3 UIs |
| `transcription` | `OpenAIWhisperEngine`, `AudioFlow.transcribe()` | Speech-to-text transcription |

## Verify installation {id="verify"}

After syncing Gradle, verify the installation by importing Kodio:

```kotlin
import space.kodio.Kodio
import space.kodio.AudioQuality

suspend fun test() {
    // If this compiles, you're good!
    val recording = Kodio.record(duration = 1.seconds)
}
```

## Next steps {id="next"}

<seealso style="cards">
    <category ref="getting-started">
        <a href="Platform-Setup.md" summary="Configure permissions and entitlements">Platform Setup</a>
        <a href="Quick-Start.md" summary="Common code patterns">Quick Start</a>
    </category>
</seealso>
