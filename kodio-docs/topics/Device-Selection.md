

**Choose devices**: Select specific microphones or speakers for recording and playback.

Kodio allows you to enumerate audio devices and direct recording or playback to specific hardware.

## List available devices {id="list-devices"}

Query the system for available audio devices:

```kotlin
// Input devices (microphones)
val inputs = Kodio.listInputDevices()
inputs.forEach { device ->
    println("🎤 ${device.name} (${device.id})")
}

// Output devices (speakers, headphones)
val outputs = Kodio.listOutputDevices()
outputs.forEach { device ->
    println("🔊 ${device.name} (${device.id})")
}
```

Each device has:

- `id`: Unique identifier
- `name`: Human-readable name (e.g., "Built-in Microphone", "AirPods Pro")

## Record from a specific device {id="record-device"}

Use an external microphone or other input device:

```kotlin
val inputs = Kodio.listInputDevices()
val externalMic = inputs.find { it.name.contains("USB") }

val recording = Kodio.record(
    duration = 5.seconds,
    device = externalMic  // null uses system default
)
```

## Play to a specific device {id="play-device"}

Route audio to headphones, speakers, or other output devices:

```kotlin
val outputs = Kodio.listOutputDevices()
val headphones = outputs.find { it.name.contains("Headphones") }

Kodio.play(recording, device = headphones)
```

## In Compose {id="compose"}

Pass the device to `rememberRecorderState`:

```kotlin
@Composable
fun DeviceSelector() {
    var selectedDevice by remember { mutableStateOf<AudioDevice?>(null) }
    val devices = remember { Kodio.listInputDevices() }
    
    // Device picker
    DropdownMenu(/* ... */) {
        devices.forEach { device ->
            DropdownMenuItem(
                text = { Text(device.name) },
                onClick = { selectedDevice = device }
            )
        }
    }
    
    // Recorder with selected device
    val recorderState = rememberRecorderState(
        device = selectedDevice
    )
    
    // ...
}
```

## Platform support {id="platforms"}

Device selection support varies by platform:


| Platform   | Input selection | Output selection | Notes                                                                                                          |
| ---------- | --------------- | ---------------- | -------------------------------------------------------------------------------------------------------------- |
| ☕ JVM      | ✅ Full          | ✅ Full           | Complete device enumeration via `javax.sound.sampled`                                                          |
| 🍏 macOS   | ✅ Full          | ✅ Full           | Resolves to a real CoreAudio device UID                                                                        |
| 🍎 iOS     | ✅ Full          | ✅ Full           | Routed via `AVAudioSession` ports                                                                              |
| 🤖 Android | ⚠️ Limited      | ⚠️ Limited       | Honoured via `AudioRecord.preferredDevice` / `AudioTrack.preferredDevice`; final routing decided by the system |
| 🌐 Web     | ❌ Not supported | ❌ Not supported  | Throws `AudioError.DeviceSelectionUnsupported` when a non-null device is passed                                |


> Pass `null` for the device parameter to use the system default — this is the safest default and works on every platform.

{style="tip"}

## Error handling {id="errors"}

If a specified device is unavailable:

```kotlin
try {
    val recording = Kodio.record(
        duration = 5.seconds,
        device = specificMic
    )
} catch (e: AudioError.DeviceNotFound) {
    // Device was disconnected or unavailable
    showError("Microphone not found. Using default.")
    val recording = Kodio.record(duration = 5.seconds)
} catch (e: AudioError.DeviceSelectionUnsupported) {
    // Running on a platform that cannot pin a specific device (e.g. browser).
    // Retry without specifying a device.
    showError("Device selection isn't supported here. Using default.")
    val recording = Kodio.record(duration = 5.seconds)
}
```

[Recording](Recording.md) [Playback](Playback.md)