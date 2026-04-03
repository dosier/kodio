[//]: # (title: Audio File I/O)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Save and load audio files</b> with <code>recording.saveAs()</code> and <code>AudioRecording.fromFile()</code> / <code>fromBytes()</code> / <code>fromSource()</code>.</p>
</tldr>

Kodio supports reading and writing audio files in common container formats. The API is designed to work across all Kotlin Multiplatform targets, including platforms without filesystem access.

## Supported formats {id="formats"}

| Format | Read | Write | Extensions |
|--------|------|-------|------------|
| WAV    | PCM int (8/16/24/32-bit), IEEE float (32/64-bit) | PCM int, IEEE float | `.wav`, `.wave` |

> More formats (AIFF, FLAC, etc.) are planned. The API is format-agnostic, so your code won't need to change when new formats are added.
>
{style="tip"}

## Saving recordings {id="saving"}

Save a recording to a WAV file on disk:

```kotlin
recording.saveAs(Path("my-recording.wav"))
```

For more control over the output, use the `AudioFlow` extensions:

```kotlin
// Write to a file
audioFlow.writeToFile(AudioFileFormat.Wav, Path("output.wav"))

// Write to any kotlinx.io Sink
audioFlow.writeToSink(AudioFileFormat.Wav, sink)

// Collect into an in-memory Buffer
val buffer = audioFlow.collectAsBuffer(AudioFileFormat.Wav)
```

## Loading audio files {id="loading"}

Kodio provides three ways to load audio files, covering every KMP platform.

### From Compose resources or byte arrays {id="from-bytes"}

Use `fromBytes()` to load audio from Compose Multiplatform resources, network responses, or any in-memory byte array:

```kotlin
// Compose resource
val recording = AudioRecording.fromBytes(
    Res.readBytes("files/notification.wav")
)

// Network response
val wavData: ByteArray = httpClient.get(url).body()
val recording = AudioRecording.fromBytes(wavData)
```

This works on **all platforms**, including web and sandboxed mobile environments.

### From a file path {id="from-file"}

Use `fromFile()` for direct filesystem access. The format is auto-detected from the file extension:

```kotlin
val recording = AudioRecording.fromFile(Path("recording.wav"))
```

> Filesystem access is available on JVM, macOS, and iOS native targets. For Android and web, prefer `fromBytes()` or `fromSource()`.
>
{style="note"}

### From a stream {id="from-source"}

Use `fromSource()` to load from any `kotlinx.io.Source`, such as an Android `ContentResolver` stream:

```kotlin
// Android ContentResolver
val source = contentResolver.openInputStream(uri)!!
    .asSource()
    .buffered()
val recording = AudioRecording.fromSource(source)
```

All three methods accept an optional `AudioFileFormat` parameter (defaults to `Wav`):

```kotlin
// Explicit format
val recording = AudioRecording.fromBytes(data, AudioFileFormat.Wav)
```

## Error handling {id="errors"}

### Read errors {id="read-errors"}

Loading can throw the following errors:

`AudioFileReadError.InvalidFile`
: The data is not a valid audio file (e.g. missing RIFF/WAVE header, corrupt structure).

`AudioFileReadError.UnsupportedFormat`
: The file uses an encoding Kodio doesn't support (e.g. compressed ADPCM).

`AudioFileReadError.IO`
: A filesystem-level error occurred while reading.

```kotlin
try {
    val recording = AudioRecording.fromFile(Path("audio.wav"))
} catch (e: AudioFileReadError.InvalidFile) {
    println("Not a valid audio file: ${e.message}")
} catch (e: AudioFileReadError.UnsupportedFormat) {
    println("Format not supported: ${e.message}")
} catch (e: AudioFileReadError.IO) {
    println("Could not read file: ${e.cause}")
}
```

### Write errors {id="write-errors"}

Saving can throw the following errors:

`AudioFileWriteError.UnsupportedFormat`
: The audio format cannot be written to the target container (e.g. planar layout to WAV).

`AudioFileWriteError.IO`
: A filesystem-level error occurred while writing.

## Platform guide {id="platforms"}

| Platform | Recommended method | Notes |
|----------|-------------------|-------|
| Compose Multiplatform | `fromBytes(Res.readBytes(...))` | Works everywhere, uses Compose resource system |
| JVM / Desktop | `fromFile(Path(...))` | Direct filesystem access |
| Android | `fromSource(inputStream.asSource())` | Use ContentResolver for user-selected files |
| iOS / macOS | `fromFile(Path(...))` or `fromBytes(...)` | Filesystem or bundled resources |
| Web (JS / Wasm) | `fromBytes(...)` | No filesystem; use fetch or bundled assets |

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Record audio from microphone">Recording</a>
        <a href="Playback.md" summary="Play back recordings">Playback</a>
        <a href="Audio-Format.md" summary="Audio format specifications">Audio Format</a>
        <a href="Error-Handling.md" summary="Handle audio errors">Error Handling</a>
    </category>
</seealso>
