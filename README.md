# Multiplatform Audio Library
A Kotlin Multiplatform library for straightforward audio recording and playback. It leverages coroutines and Flow to provide a modern, asynchronous API for handling audio streams across JVM, Android, and iOS.

## Core Concepts
- **`AudioSystem`**: The main entry point for all audio operations. Get the default platform-specific implementation via `SystemAudioSystem`.
- **`AudioDevice`**: Represents hardware for audio input (microphones) and output (speakers). List available devices from the `AudioSystem`.
- **`AudioFlow`**: A stream of audio data, combining a `Flow<ByteArray>` of audio chunks with an `AudioFormat`.
- **`RecordingSession`**: Manages a recording from an input device. Use `start(format)` to begin capturing audio as an `AudioFlow`. You can observe its state (`Recording`, `Stopped`).
- **`PlaybackSession`**: Manages playback to an output device. Use `play(audioFlow)` to start sending audio. Control it with `pause`, `resume`, and `stop`. You can observe its state (`Playing`, `Paused`, `Finished`).

## Usage Example
Here’s a simple loopback example that records audio and plays it back immediately.
```Kotlin
kotlin import kotlinx.coroutines.delay import kotlinx.coroutines.runBlocking
fun main() = runBlocking {
    val audioSystem = SystemAudioSystem
   
   // 1. Get default input and output devices
   val inputDevice = audioSystem.listInputDevices().firstOrNull() ?: error("No input device found")
   val outputDevice = audioSystem.listOutputDevices().firstOrNull() ?: error("No output device found")
   
   // 2. Create recording and playback sessions
   val recording = audioSystem.createRecordingSession(inputDevice)
   val playback = audioSystem.createPlaybackSession(outputDevice)
   
   // 3. Start recording to get an audio flow
   val format = inputDevice.formatSupport.defaultFormat
   val audioFlow = recording.start(format)
   
   // 4. Play the recorded audio flow
   playback.play(audioFlow)
   
   println("Recording and playing back for 10 seconds...")
   delay(10_000)
   
   // 5. Stop the sessions
   recording.stop()
   playback.stop()
   println("Done.")
}
```


## Platform-Specific Setup

### Android
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

### iOS
1. **Permissions**: You must provide a description for microphone usage in your `Info.plist` file. Add the `NSMicrophoneUsageDescription` key with a string explaining why your app needs microphone access.
2. **Device Selection**: On iOS, it is not possible to programmatically select a specific audio output device. The `PlaybackSession` will always use the system's current default output.

### JVM
No special setup is required. The library should work out of the box on any system with available audio input and output devices.
