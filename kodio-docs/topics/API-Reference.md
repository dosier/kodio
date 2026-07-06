[//]: # (title: API Reference)

<show-structure for="chapter" depth="2"/>

Quick reference for all Kodio APIs.

This page is a hand-written quick reference. For the full generated API documentation (all classes, functions, and KDoc comments), see the [KDoc API Reference](%api-docs%).

## Kodio {id="kodio"}

```kotlin
// Recording
suspend fun record(duration: Duration, quality: AudioQuality = Default, device: Input? = null): AudioRecording
suspend fun <T> record(quality: AudioQuality = Default, device: Input? = null, block: suspend (Recorder) -> T): T
fun recorder(quality: AudioQuality = Default, device: Input? = null): Recorder

// Playback
suspend fun play(recording: AudioRecording, device: Output? = null)
suspend fun <T> play(recording: AudioRecording, device: Output? = null, block: suspend (Player) -> T): T
suspend fun play(audioFlow: AudioFlow, device: Output? = null)
fun player(device: Output? = null): Player

// Devices (suspend)
suspend fun listInputDevices(): List<AudioDevice.Input>
suspend fun listOutputDevices(): List<AudioDevice.Output>

// Permissions
val microphonePermission: AudioPermissionManager
```

## Recorder {id="recorder"}

```kotlin
recorder.start()
recorder.stop()
recorder.toggle()
recorder.reset()
recorder.release()
suspend fun getRecording(): AudioRecording?

val isRecording: Boolean
val hasRecording: Boolean
val liveAudioFlow: Flow<ByteArray>?
val audioFlow: AudioFlow?

recorder.use { r -> ... }
```

## Player {id="player"}

```kotlin
suspend fun load(recording: AudioRecording)
suspend fun loadAudioFlow(audioFlow: AudioFlow)
player.start()
player.pause()
player.resume()
player.stop()
player.toggle()
player.release()
suspend fun awaitComplete()

val isPlaying: Boolean
val isPaused: Boolean
val isReady: Boolean
val isFinished: Boolean

player.use { p -> ... }
```

## AudioRecording {id="audio-recording"}

```kotlin
suspend fun play()
suspend fun saveAs(path: Path, fileFormat: AudioFileFormat = Wav)
fun toByteArray(): ByteArray
fun asFlow(defensiveCopy: Boolean = true): Flow<ByteArray>

val format: AudioFormat
val calculatedDuration: Duration
val sizeInBytes: Long

// Raw PCM
fromBytes(format: AudioFormat, data: ByteArray)
fromChunks(format: AudioFormat, chunks: List<ByteArray>, duration: Duration? = null)
fromAudioFlow(audioFlow: AudioFlow, duration: Duration? = null)

// Container files (space.kodio.core.io.files)
fromBytes(bytes: ByteArray, fileFormat: AudioFileFormat = Wav)
fromSource(source: Source, fileFormat: AudioFileFormat = Wav)
fromFile(path: Path, fileSystem: FileSystem = SystemFileSystem)
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
AudioError.DeviceNotFound(deviceId?)
AudioError.FormatNotSupported(format?)
AudioError.DeviceError(errorMessage, errorCause?)
AudioError.NotInitialized
AudioError.AlreadyRecording
AudioError.AlreadyPlaying
AudioError.NoRecordingData
AudioError.DeviceSelectionUnsupported(kind)
AudioError.Unknown(originalCause)
```

## Compose {id="compose"}

```kotlin
rememberRecorderState(quality?, device?, liveWaveformGain?, onRecordingComplete?)
rememberPlayerState(device?, onPlaybackComplete?)
rememberPlayerState(recording, device?, onPlaybackComplete?)

AudioWaveform(amplitudes, modifier?, style?, colors?, progress?, onProgressChange?, animate?, animationSpec?)
```

## Material 3 {id="material3"}

```kotlin
RecordAudioButton(modifier?, state?, showWaveform?)
PlayAudioButton(recording, modifier?, state?, showWaveform?)
AudioPermissionButton(state, modifier?)
ErrorDialog(error, onDismiss)
```

## Transcription {id="transcription"}

```kotlin
OpenAIWhisperEngine(apiKey, model?, httpClient?, chunkDurationSeconds?, endpointUrl?, additionalHeaders?)
audioFlow.transcribe(engine, config = TranscriptionConfig.Default)
recorder.transcribe(engine, config = TranscriptionConfig.Default)

TranscriptionConfig(language = "en-US")
TranscriptionConfig.Default
```

<seealso style="cards">
    <category ref="external">
        <a href="%api-docs%">KDoc API Reference</a>
    </category>
</seealso>
