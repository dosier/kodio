![Maven Central Version](https://img.shields.io/maven-central/v/space.kodio/core)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)

> [!CAUTION]  
> This library is still in very early development.

# Kotlin Multiplatform Audio Library
Kodio is a Kotlin Multiplatform library for straightforward audio recording and playback. It leverages coroutines and Flow to provide a modern, asynchronous API for handling audio streams across JVM, Android, Web (JS/Wasm), and iOS.

## Usage Example
Here’s a simple loopback example that records audio for 5 seconds and then plays it back.
```Kotlin
suspend fun main() {
   // Record some audio for 5 seconds
   val recording = SystemAudioSystem.createRecordingSession()
   recording.start()
   delay(5000)
   recording.stop()
   // Playback the recorded audio
   val playback = SystemAudioSystem.createPlaybackSession()
   playback.play(recording.audioflow.value)
}
```
## Setup

### Gradle
```Kotlin
dependencies {
    implementation("space.kodio:core:0.0.4")
}
```

### Platform-Specific Setup

#### Android
1. **Manifest permission**: Add the `RECORD_AUDIO` permission to your `AndroidManifest.xml`:
    ```xml
        <uses-permission android:name="android.permission.RECORD_AUDIO" />
    ```
   You are also responsible for handling the runtime permission request dialog. If permission is denied, the library will throw an `AudioPermissionDeniedException`.
2. **Initialization**: Before using the library, you must initialize the `AndroidAudioSystem` with an application `Context` and an `Activity` (which is used to launch the permission request dialog). This is typically done in your `Application` or `MainActivity`'s `onCreate` method.
    ```kotlin
        // In your MainActivity or Application class
        AndroidAudioSystem.setApplicationContext(applicationContext)
        AndroidAudioSystem.setMicrophonePermissionRequestActivity(this)
    ```
3. **Handle runtime permission requests**: The library will automatically handle runtime permission requests when a `RecordingSession` is created. If the user denies the permission, the library will throw `AudioPermissionDeniedException`. In your MainActivity override the following:
   ```kotlin
       override fun onRequestPermissionsResult(
           requestCode: Int,
           permissions: Array<out String?>,
           grantResults: IntArray,
           deviceId: Int
       ) {
           //...
           AndroidAudioSystem.onRequestPermissionsResult(requestCode, grantResults)
       }
   ```

#### iOS
1. **Permissions**: You must provide a description for microphone usage in your `Info.plist` file. Add the `NSMicrophoneUsageDescription` key with a string explaining why your app needs microphone access.
2. **Device Selection**: On iOS, it is not possible to programmatically select a specific audio output device. The `PlaybackSession` will always use the system's current default output.

#### JVM
No special setup is required. The library should work out of the box on any system with available audio input and output devices.

## Credits
This project is inspired by https://github.com/theolm/kmp-record
