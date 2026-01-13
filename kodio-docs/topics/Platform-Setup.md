[//]: # (title: Platform Setup)

<show-structure for="chapter" depth="2"/>

<tldr>
<p><b>Platform requirements</b>: Android needs initialization, Apple platforms need Info.plist entries, Web needs HTTPS.</p>
</tldr>

Each platform has specific requirements for audio recording. This guide walks you through the setup for each supported platform.

## Android {id="android"}

Android requires both a manifest permission and runtime initialization.

<procedure title="Set up Android" id="android-setup">
<step>

Add the microphone permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

</step>
<step>

Initialize Kodio in your `Application` class before recording:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kodio.initialize(this)
    }
}
```

</step>
<step>

Make sure your Application class is registered in the manifest:

```xml
<application
    android:name=".MyApp"
    ...>
```

</step>
</procedure>

> **Important**: You must call `Kodio.initialize()` before any recording operations. If you forget, Kodio will throw an `AudioError.NotInitialized` error.
>
{style="warning"}

### Runtime permissions {id="android-runtime-permissions" collapsible="true"}

On Android 6.0+, you also need to request the permission at runtime. If you're using Compose, `RecorderState` handles this automatically:

```kotlin
val recorderState = rememberRecorderState()

if (recorderState.needsPermission) {
    Button(onClick = { recorderState.requestPermission() }) {
        Text("Grant Microphone Access")
    }
}
```

## iOS {id="ios"}

iOS requires a usage description in your Info.plist explaining why your app needs microphone access.

<procedure title="Set up iOS" id="ios-setup">
<step>

Open your Xcode project and locate `Info.plist` (or your app's info dictionary).

</step>
<step>

Add the microphone usage description key:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio.</string>
```

</step>
</procedure>

> Customize the description string to match your app's use case. Apple reviews these descriptions during app review.
>
{style="tip"}

## macOS {id="macos"}

macOS requires both an Info.plist entry and a sandbox entitlement.

<procedure title="Set up macOS" id="macos-setup">
<step>

Add the microphone usage description to `Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio.</string>
```

</step>
<step>

Add the audio input entitlement to your `.entitlements` file:

```xml
<key>com.apple.security.device.audio-input</key>
<true/>
```

</step>
</procedure>

## JVM (Desktop) {id="jvm"}

No setup required. ✅

Kodio uses the Java Sound API which is available on all JVM platforms. Recording and playback work out of the box.

```kotlin
fun main() = runBlocking {
    val recording = Kodio.record(duration = 5.seconds)
    recording.play()
}
```

## Web (JS / Wasm) {id="web"}

Web platforms require HTTPS and browser permission prompts.

<procedure title="Set up Web" id="web-setup">
<step>

Ensure your site is served over **HTTPS** (or localhost for development). Browsers block microphone access on insecure origins.

</step>
<step>

That's it! The browser will automatically prompt the user for microphone permission when you call `Kodio.record()`.

</step>
</procedure>

### Browser compatibility {id="browser-compat" collapsible="true"}

| Browser | Support |
|---------|---------|
| Chrome | ✅ Full support |
| Firefox | ✅ Full support |
| Safari | ✅ Full support |
| Edge | ✅ Full support |

> Users must grant permission through the browser's native dialog. There's no way to bypass this.
>
{style="note"}

## Quick reference {id="quick-reference"}

| Platform | Permission | Initialization | Extra |
|----------|------------|----------------|-------|
| 🤖 Android | Manifest + Runtime | `Kodio.initialize(context)` | — |
| 🍎 iOS | Info.plist | — | — |
| 🍏 macOS | Info.plist | — | Entitlement |
| ☕ JVM | — | — | — |
| 🌐 Web | Browser prompt | — | HTTPS |
