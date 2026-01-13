[//]: # (title: API Reference)

<show-structure for="chapter" depth="2"/>

Quick reference for all Kodio APIs.

## Kodio {id="kodio"}

```kotlin
// Recording
Kodio.record(duration, quality?, device?)
Kodio.record { recorder -> ... }
Kodio.recorder(quality?, device?)

// Playback
Kodio.play(recording, device?)
Kodio.play(recording) { player -> ... }
Kodio.player(device?)

// Devices
Kodio.listInputDevices()
Kodio.listOutputDevices()

// Permissions
Kodio.microphonePermission
```

## Recorder {id="recorder"}

```kotlin
recorder.start()
recorder.stop()
recorder.toggle()
recorder.reset()
recorder.release()
recorder.getRecording()

recorder.isRecording
recorder.hasRecording
recorder.liveAudioFlow

recorder.use { r -> ... }
```

## Player {id="player"}

```kotlin
player.load(recording)
player.start()
player.pause()
player.resume()
player.stop()
player.toggle()
player.release()
player.awaitComplete()

player.isPlaying
player.isPaused
player.isReady
player.isFinished

player.use { p -> ... }
```

## AudioRecording {id="audio-recording"}

```kotlin
recording.play()
recording.saveAs(path)
recording.toByteArray()
recording.asFlow()

recording.format
recording.calculatedDuration
recording.sizeInBytes

AudioRecording.fromBytes(format, data)
AudioRecording.fromChunks(format, chunks)
```

## AudioQuality {id="audio-quality"}

```kotlin
AudioQuality.Voice      // 16kHz, Mono
AudioQuality.Standard   // 44.1kHz, Mono (default)
AudioQuality.High       // 48kHz, Stereo
AudioQuality.Lossless   // 96kHz, Stereo, 24-bit
```

## AudioError {id="audio-error"}

```kotlin
AudioError.PermissionDenied
AudioError.DeviceNotFound
AudioError.FormatNotSupported
AudioError.DeviceError
AudioError.NotInitialized
AudioError.AlreadyRecording
AudioError.AlreadyPlaying
AudioError.NoRecordingData
AudioError.Unknown
```

## Compose {id="compose"}

```kotlin
val recorderState = rememberRecorderState(quality?, device?)
val playerState = rememberPlayerState(recording?, device?)

AudioWaveform(amplitudes, barColor?, brush?)
```

## Material 3 {id="material3"}

```kotlin
RecordAudioButton(isRecording, isProcessing, onClick)
PlayAudioButton(isPlaying, isPaused, isReady, isFinished, onClick)
AudioPermissionButton(onClick)
ErrorDialog(error, onDismiss)
```
