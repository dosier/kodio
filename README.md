# Multiplatform Audio Library
A Kotlin Multiplatform library for straightforward audio recording and playback. It leverages coroutines and Flow to provide a modern, asynchronous API for handling audio streams across JVM, Android, and iOS.

## Core Concepts
The library is designed around a few central components:
- **`AudioSystem`**: The main entry point for all audio operations. You can get the default, platform-specific implementation using the property. `SystemAudioSystem`
- **`AudioDevice`**: Represents hardware for audio input (microphones) and output (speakers). You can get a list of available devices from the . `AudioSystem`
- **`RecordingSession`**: Manages a recording from a specific input device. It provides the captured audio as a and exposes its current state (e.g., , ). `Flow<ByteArray>``Recording``Stopped`
- **`PlaybackSession`**: Manages playback on a specific output device. You can control it with , , , and , and observe its state (e.g., , , ). `play``pause``resume``stop``Playing``Paused``Finished`
- **`AudioDataFlow`**: A convenient wrapper that bundles a raw with its . This ensures that a knows how to correctly interpret the audio data it receives. `Flow<ByteArray>``AudioFormat``PlaybackSession`
- **`AudioFormat`**: A simple data class that defines the properties of an audio stream: , , and . `sampleRate``bitDepth``channels`

## Usage Example
Here’s a complete example of a simple loopback that records audio from the default microphone and immediately plays it through the default speaker.

```Kotlin
import com.example.audio.SystemAudioSystem
import com.example.audio.asAudioDataFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val audioSystem = SystemAudioSystem

    // 1. Find an input and output device
    val inputDevice = audioSystem.listInputDevices().firstOrNull()
    val outputDevice = audioSystem.listOutputDevices().firstOrNull()

    if (inputDevice == null || outputDevice == null) {
        println("Default input/output devices not found.")
        return@runBlocking
    }

    println("Found input device: ${inputDevice.name}")
    println("Found output device: ${outputDevice.name}")

    // 2. Create recording and playback sessions
    val recordingSession = audioSystem.createRecordingSession(inputDevice)
    val playbackSession = audioSystem.createPlaybackSession(outputDevice)

    // 3. Start recording with a desired format
    val audioFormat = inputDevice.formatSupport.defaultFormat
    recordingSession.start(audioFormat)

    // 4. Pipe the recording flow into the playback session
    // The .asAudioDataFlow() extension is used to package the flow with its format
    val audioDataFlow = recordingSession.audioDataFlow.asAudioDataFlow(audioFormat)
    playbackSession.play(audioDataFlow)

    // 5. Concurrently monitor the states of both sessions
    val job = launch {
        launch {
            recordingSession.state.collect { state ->
                println("Recording state -> $state")
            }
        }
        launch {
            playbackSession.state.collect { state ->
                println("Playback state -> $state")
            }
        }
    }

    // Let the loopback run for 10 seconds
    println("\nStarting 10-second audio loopback...\n")
    delay(10_000)

    // 6. Stop the sessions and clean up
    println("\nStopping sessions...")
    recordingSession.stop()
    playbackSession.stop()
    job.cancel()
}
```

## Platform-Specific Setup

### Android
1. **Permissions**: Add the permission to your : `RECORD_AUDIO``AndroidManifest.xml`
```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
```
You are also responsible for handling the runtime permission request dialog. If permission is denied, the library will throw an . `AudioPermissionDeniedException`
2. **Initialization**: Before using the library, you must initialize the with an application and an (which is used to launch the permission request dialog). This is typically done in your `Application` or 's `onCreate` method. `AndroidAudioSystem``Context``Activity``MainActivity`
```Kotlin
    // In your MainActivity or Application class
    AndroidAudioSystem.setApplicationContext(applicationContext)
    AndroidAudioSystem.setMicrophonePermissionRequestActivity(this)
```
### iOS
1. **Permissions**: You must provide a description for microphone usage in your file. Add the `NSMicrophoneUsageDescription` key with a string explaining why your app needs microphone access. `Info.plist`
2. **Device Selection**: On iOS, it is not possible to programmatically select a specific audio output device. The will always use the system's current default output. `PlaybackSession`

### JVM
No special setup is required. The library should work out of the box on any system with available audio input and output devices.
