[//]: # (title: Recording)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Record audio</b> with <code>Kodio.record()</code> for timed recording or <code>Kodio.recorder()</code> for manual control.</p>
</tldr>

Kodio provides two approaches to recording: a simple one-liner for timed recording, and a more flexible `Recorder` class for manual control.

## Timed recording {id="timed"}

The simplest way to record audio is with `Kodio.record()`. Pass a duration and Kodio handles the rest—starting, stopping, and packaging the audio into a recording.

```kotlin
val recording = Kodio.record(duration = 5.seconds)
```

You can also specify a quality preset to optimize for your use case:

```kotlin
val recording = Kodio.record(
    duration = 5.seconds,
    quality = AudioQuality.High  // 48 kHz stereo
)
```

> See [Audio Quality](Audio-Quality.md) for all available presets and their specifications.
>
{style="tip"}

## Manual control {id="manual"}

When you need to control when recording starts and stops (for example, based on user interaction), use the callback form of `Kodio.record()`:

```kotlin
val recording = Kodio.record { recorder ->
    recorder.start()
    waitForUserStop()  // Your app logic
    recorder.stop()
    recorder.getRecording()
}
```

The lambda receives a `Recorder` instance and returns whatever the lambda returns—typically the recording itself.

## Using Recorder directly {id="recorder"}

For maximum control, create a `Recorder` instance directly. This is useful when you need to:
- Start and stop recording multiple times
- Access live audio data
- Manage the recorder's lifecycle explicitly

```kotlin
val recorder = Kodio.recorder(quality = AudioQuality.Standard)

recorder.use { r ->
    r.start()
    delay(5.seconds)
    r.stop()
    r.getRecording()?.saveAs(Path("audio.wav"))
}
```

The `use` extension ensures resources are properly released, even if an exception occurs.

## Live audio processing {id="live-audio"}

The `Recorder` provides a `liveAudioFlow` that emits audio chunks in real-time while recording. This is perfect for visualizations like waveforms or level meters.

```kotlin
recorder.liveAudioFlow?.collect { chunk ->
    val amplitude = calculateAmplitude(chunk)
    updateWaveform(amplitude)
}
```

> In Compose, use `RecorderState.liveAmplitudes` for pre-calculated amplitude values ready for visualization. See [RecorderState](Recorder-State.md).
>
{style="tip"}

## Recorder API reference {id="api-reference"}

### Properties {id="properties" collapsible="true"}

<deflist type="medium">
<def title="isRecording: Boolean">
<code>true</code> while actively recording audio.
</def>
<def title="hasRecording: Boolean">
<code>true</code> if a recording is available via <code>getRecording()</code>.
</def>
<def title="quality: AudioQuality">
The quality preset used for this recorder.
</def>
<def title="liveAudioFlow: Flow<ByteArray>?">
Real-time audio data while recording. May be <code>null</code> on some platforms.
</def>
<def title="stateFlow: StateFlow<State>">
Observable state changes for reactive UIs.
</def>
</deflist>

### Methods {id="methods" collapsible="true"}

<deflist type="medium">
<def title="start()">
Begin recording audio.
</def>
<def title="stop()">
Stop recording. The recording becomes available via <code>getRecording()</code>.
</def>
<def title="toggle()">
Start if stopped, stop if recording. Convenient for single-button UIs.
</def>
<def title="reset()">
Discard the current recording and prepare for a new one.
</def>
<def title="release()">
Release all resources. Called automatically when using <code>use {}</code>.
</def>
<def title="getRecording(): AudioRecording?">
Get the completed recording, or <code>null</code> if none available.
</def>
</deflist>

<seealso style="cards">
    <category ref="core-api">
        <a href="Playback.md" summary="Play back recorded audio">Playback</a>
        <a href="Audio-Quality.md" summary="Quality presets explained">Audio Quality</a>
        <a href="Error-Handling.md" summary="Handle recording errors">Error Handling</a>
    </category>
</seealso>
