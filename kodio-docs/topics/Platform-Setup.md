

**Platform requirements**: Android needs initialization, Apple platforms need Info.plist entries, Web needs HTTPS.

Each platform has specific requirements for audio recording. This guide walks you through the setup for each supported platform.

## Android {id="android"}

Android requires both a manifest permission and runtime initialization.



Add the microphone permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```



Initialize Kodio before recording. The recommended approach is to call `Kodio.initialize()` from your main Activity, which wires up both the application context and permission handling in one step:



```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Kodio.initialize(this) // sets context + permission activity
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String?>,
        grantResults: IntArray, deviceId: Int
    ) {
        Kodio.onRequestPermissionsResult(requestCode, grantResults)
    }
}
```



If you prefer initializing in your `Application` class, note that this only sets the application context. Permission handling must be wired separately from an Activity.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kodio.initialize(this) // context only
    }
}
```

Register it in your manifest:

```xml
<application
    android:name=".MyApp"
    ...>
```





> **Important**: You must call `Kodio.initialize()` before any recording operations. If you forget, Kodio will throw an `AudioError.NotInitialized` error.

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

### Recording in the Android emulator {id="android-emulator" collapsible="true"}

Recording on the Android emulator routes audio through QEMU's audio bridge,
which resamples and re-quantises the host microphone signal before handing it
to the guest's `AudioRecord`. On macOS/Apple-silicon hosts this commonly
produces audibly noisy or grainy playback even when the host mic is clean
(GitHub issue #1).

Mitigations that help in practice:

1. **Match the host's preferred format.** Request `AudioQuality.High`
  (48 kHz / Int16 / mono) or `AudioQuality.Standard` so the emulator's
   audio bridge does not need to resample.
2. **Configure the AVD to use the host audio backend directly.** From the
  command line:
3. **Test on a physical device** for production audio quality validation —
  the emulator bridge is a known weak link and is not representative of
   on-device capture.

If the noise persists after the steps above, please attach a host-mic recording
plus the emulator-recorded version to the GitHub issue so we can compare.

## iOS {id="ios"}

iOS requires a usage description in your Info.plist explaining why your app needs microphone access.



Open your Xcode project and locate `Info.plist` (or your app's info dictionary).



Add the microphone usage description key:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio.</string>
```



> Customize the description string to match your app's use case. Apple reviews these descriptions during app review.

{style="tip"}

## macOS {id="macos"}

macOS requires both an Info.plist entry and a sandbox entitlement for production apps.



Add the microphone usage description to `Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio.</string>
```



Add the audio input entitlement to your `.entitlements` file:

```xml
<key>com.apple.security.device.audio-input</key>
<true/>
```



### Native audio backend {id="macos-native-audio"}

On macOS, Kodio uses native CoreAudio via Panama FFI for optimal audio quality and device support. This requires **Java 21 or later**.

If the native library isn't available, Kodio automatically falls back to JavaSound.

### Development troubleshooting {id="macos-dev-troubleshooting" collapsible="true"}

When running from an IDE or Terminal during development, you may encounter silent audio (all zeros). This happens because macOS grants microphone permissions **per-app**, not per-process.

**Solution**: Go to **System Settings → Privacy & Security → Microphone** and enable access for:

- Your IDE (IntelliJ IDEA, Android Studio, Cursor, etc.)
- Terminal.app (if running from command line)

> The Kodio permission check may report "Granted" while audio is silent. This is because the permission API checks AVFoundation permissions, but audio capture uses CoreAudio which has a separate permission scope tied to the parent app.

{style="warning"}

## JVM (Desktop) {id="jvm"}

No setup required for basic usage. ✅

Kodio uses the Java Sound API which is available on all JVM platforms. Recording and playback work out of the box.

```kotlin
fun main() = runBlocking {
    val recording = Kodio.record(duration = 5.seconds)
    recording.play()
}
```

### System properties {id="jvm-system-properties" collapsible="true"}

Kodio supports the following system properties for JVM configuration:


| Property                           | Default | Description                                                                                                                                                              |
| ---------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `kodio.useJavaSound`               | `false` | Force using JavaSound (javax.sound.sampled) instead of native CoreAudio on macOS.                                                                                        |
| `kodio.jvm.recording.warmupMillis` | `200`   | Number of milliseconds of audio read-and-discarded after the JavaSound `TargetDataLine` starts, to swallow the priming silence (GitHub issue #5). Set to `0` to disable. |


**When to use `kodio.useJavaSound`:**

- Debugging audio issues on macOS
- If the native CoreAudio library fails to load
- When you need guaranteed cross-platform behavior

**When to tune `kodio.jvm.recording.warmupMillis`:**

- Lower it (e.g. `50`) if you're capturing extremely short snippets and the
warmup is eating real audio
- Raise it (e.g. `400`) if your JDK / OS combination has a slower priming
ramp than 200 ms — symptoms are silence at the start of recordings on JVM
- Set it to `0` to disable the drain entirely (useful when measuring raw
JavaSound latency)

Set it in code before any Kodio calls:

```kotlin
fun main() {
    System.setProperty("kodio.useJavaSound", "true")
    
    runBlocking {
        val recording = Kodio.record(duration = 5.seconds)
        recording.play()
    }
}
```

Or via command line:

```bash
java -Dkodio.useJavaSound=true -jar your-app.jar
```

## Web (JS / Wasm) {id="web"}

Web platforms require HTTPS and browser permission prompts.



Ensure your site is served over **HTTPS** (or localhost for development). Browsers block microphone access on insecure origins.



That's it! The browser will automatically prompt the user for microphone permission when you call `Kodio.record()`.



### Browser compatibility {id="browser-compat" collapsible="true"}


| Browser | Support        |
| ------- | -------------- |
| Chrome  | ✅ Full support |
| Firefox | ✅ Full support |
| Safari  | ✅ Full support |
| Edge    | ✅ Full support |


> Users must grant permission through the browser's native dialog. There's no way to bypass this.

{style="note"}

## Quick reference {id="quick-reference"}


| Platform   | Permission         | Initialization               | Extra                       |
| ---------- | ------------------ | ---------------------------- | --------------------------- |
| 🤖 Android | Manifest + Runtime | `Kodio.initialize(activity)` | —                           |
| 🍎 iOS     | Info.plist         | —                            | —                           |
| 🍏 macOS   | Info.plist         | —                            | Entitlement, Java 21+       |
| ☕ JVM      | —                  | —                            | `kodio.useJavaSound` option |
| 🌐 Web     | Browser prompt     | —                            | HTTPS                       |


