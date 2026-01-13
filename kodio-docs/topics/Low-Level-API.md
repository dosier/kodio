[//]: # (title: Low-Level API)

<show-structure for="chapter" depth="2"/>

Direct session access for advanced use cases like custom audio processing pipelines.

> For most use cases, prefer the high-level [Recorder](Recording.md) and [Player](Playback.md) APIs.
>
{style="warning"}

## Recording session {id="recording-session"}

```kotlin
val session = SystemAudioSystem.createRecordingSession()

session.start()
delay(5.seconds)
session.stop()

val audioFlow = session.audioFlow.value
val recording = AudioRecording.fromAudioFlow(audioFlow!!)
```

## Playback session {id="playback-session"}

```kotlin
val session = SystemAudioSystem.createPlaybackSession()

session.load(audioFlow)
session.play()
session.state.first { it is AudioPlaybackSession.State.Finished }
session.stop()
```

## Live processing {id="live-processing"}

Process audio in real-time while recording:

```kotlin
val session = SystemAudioSystem.createRecordingSession()
session.start()

session.audioFlow.value?.collect { chunk ->
    val amplitude = calculateRMS(chunk)
    updateWaveform(amplitude)
}
```

## AudioFlow {id="audio-flow"}

```kotlin
val audioFlow = AudioFlow(
    format = AudioFormat(
        sampleRate = 44100,
        channels = Channels.Mono,
        encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
    ),
    data = flow { emit(chunk) }
)
```

## SystemAudioSystem {id="system-audio"}

```kotlin
val inputs = SystemAudioSystem.listInputDevices()
val outputs = SystemAudioSystem.listOutputDevices()
val recordingSession = SystemAudioSystem.createRecordingSession(device)
val playbackSession = SystemAudioSystem.createPlaybackSession(device)
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md"/>
        <a href="Playback.md"/>
    </category>
</seealso>
