[//]: # (title: Device Selection)

<show-structure for="chapter" depth="2"/>
<primary-label ref="advanced"/>

<tldr>
<p><b>Choose devices</b>: Select specific microphones or speakers for recording and playback.</p>
</tldr>

Kodio allows you to enumerate audio devices and direct recording or playback to specific hardware.

## List available devices {id="list-devices"}

`Kodio.listInputDevices()` and `Kodio.listOutputDevices()` are suspend functions. Call them from a coroutine:

```kotlin
suspend fun listDevices() {
    // Input devices (microphones)
    val inputs = Kodio.listInputDevices()
    inputs.forEach { device ->
        println("Input: ${device.name} (${device.id})")
    }

    // Output devices (speakers, headphones)
    val outputs = Kodio.listOutputDevices()
    outputs.forEach { device ->
        println("Output: ${device.name} (${device.id})")
    }
}
```

Each device has:

- `id`: Unique identifier
- `name`: Human-readable name (for example "Built-in Microphone", "AirPods Pro")

## Record from a specific device {id="record-device"}

Use an external microphone or other input device:

```kotlin
suspend fun recordFromDevice() {
    val inputs = Kodio.listInputDevices()
    val externalMic = inputs.find { it.name.contains("USB") }

    val recording = Kodio.record(
        duration = 5.seconds,
        device = externalMic  // null uses system default
    )
}
```

## Play to a specific device {id="play-device"}

Route audio to headphones, speakers, or other output devices:

```kotlin
suspend fun playToDevice(recording: AudioRecording) {
    val outputs = Kodio.listOutputDevices()
    val headphones = outputs.find { it.name.contains("Headphones") }

    Kodio.play(recording, device = headphones)
}
```

## In Compose {id="compose"}

Load devices in a coroutine and pass the selected device to `rememberRecorderState`:

```kotlin
@Composable
fun DeviceSelector() {
    var selectedDevice by remember { mutableStateOf<AudioDevice.Input?>(null) }
    var devices by remember { mutableStateOf<List<AudioDevice.Input>>(emptyList()) }

    LaunchedEffect(Unit) {
        devices = Kodio.listInputDevices()
    }

    DropdownMenu(/* ... */) {
        devices.forEach { device ->
            DropdownMenuItem(
                text = { Text(device.name) },
                onClick = { selectedDevice = device }
            )
        }
    }

    val recorderState = rememberRecorderState(device = selectedDevice)
    // ...
}
```

## Platform support {id="platforms"}

Device selection support varies by platform:

| Platform   | Input selection | Output selection | Notes                                                                                                          |
| ---------- | --------------- | ---------------- | -------------------------------------------------------------------------------------------------------------- |
| JVM        | Full            | Full             | Complete device enumeration via `javax.sound.sampled`                                                          |
| macOS      | Full            | Full             | Resolves to a real CoreAudio device UID                                                                        |
| iOS        | Full            | Full             | Routed via `AVAudioSession` ports                                                                              |
| Android    | Limited         | Limited          | Honoured via `AudioRecord.preferredDevice` / `AudioTrack.preferredDevice`; final routing decided by the system |
| Web        | Not supported   | Not supported    | Throws `AudioError.DeviceSelectionUnsupported` when a non-null device is passed                                |

On web, output device selection via `setSinkId` is not implemented. Passing a non-null output device yields `AudioError.DeviceSelectionUnsupported`.

> Pass `null` for the device parameter to use the system default. This works on every platform.

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
    // Platform cannot pin a specific device (for example browser output selection).
    showError("Device selection isn't supported here. Using default.")
    val recording = Kodio.record(duration = 5.seconds)
}
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Recording API">Recording</a>
        <a href="Playback.md" summary="Playback API">Playback</a>
    </category>
</seealso>
