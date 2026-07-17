[//]: # (title: Audio File I/O)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Save and load audio files</b> with <code>recording.saveAs()</code> and <code>AudioRecording.fromFile()</code> / <code>fromBytes()</code> / <code>fromSource()</code>. Read tags with <code>AudioFileReader.readMetadata()</code>.</p>
</tldr>

Kodio supports reading and writing audio files in common container formats. The API works across all Kotlin Multiplatform targets, including platforms without filesystem access.

## Supported formats {id="formats"}

| Format | Read | Write | Extensions |
|--------|------|-------|------------|
| WAV    | PCM int (8/16/24/32-bit), IEEE float (32/64-bit) | PCM int, IEEE float | `.wav`, `.wave` |
| AIFF / AIFF-C | PCM int (8/16/24/32-bit), IEEE float (`fl32`/`fl64`) | PCM int, IEEE float | `.aiff`, `.aif` |
| AU     | PCM int (8/16/24/32-bit), IEEE float (32/64-bit) | PCM int, IEEE float | `.au`, `.snd` |
| MP3    | MPEG-1 Layer III to PCM (decode only) | Not supported | `.mp3` |

Specify the format with `AudioFileFormat.Wav`, `AudioFileFormat.Aiff`, `AudioFileFormat.Au`, or `AudioFileFormat.Mp3`.

## Format detection {id="detection"}

Loaders probe **magic bytes** first, then fall back to the file name extension. There is no silent default to WAV.

1. Explicit `fileFormat` argument wins.
2. Otherwise magic-byte sniff (`RIFF`/`WAVE`, `FORM`/`AIFF`/`AIFC`, `.snd`, `ID3` or a validated MPEG frame header).
3. Otherwise known extension from `fileName` / path.
4. Magic wins if it disagrees with the extension.
5. If still unknown, loaders throw `AudioFileReadError.UnsupportedFormat`. Empty input throws `InvalidFile`.

```kotlin
val format = AudioFileFormat.detect(bytes)           // nullable
    ?: AudioFileFormat.fromExtension("mp3")

val recording = AudioRecording.fromBytes(bytes, fileName = "song.mp3")
val forced = AudioRecording.fromBytes(bytes, AudioFileFormat.Mp3)
```

## Saving recordings {id="saving"}

Save a recording to disk. The default format is WAV:

```kotlin
recording.saveAs(Path("my-recording.wav"))
```

Save as AIFF or AU:

```kotlin
recording.saveAs(Path("my-recording.aiff"), fileFormat = AudioFileFormat.Aiff)
recording.saveAs(Path("my-recording.au"), fileFormat = AudioFileFormat.Au)
```

MP3 encode is not supported and throws `AudioFileWriteError.UnsupportedFormat`.

For more control over the output, use the `AudioFlow` extensions:

```kotlin
audioFlow.writeToFile(AudioFileFormat.Wav, Path("output.wav"))
audioFlow.writeToSink(AudioFileFormat.Au, sink)
val buffer = audioFlow.collectAsBuffer(AudioFileFormat.Wav)
```

## Loading audio files {id="loading"}

### From Compose resources or byte arrays {id="from-bytes"}

```kotlin
// Auto-detect from magic bytes (optional fileName helps when magic is weak)
val recording = AudioRecording.fromBytes(
    Res.readBytes("files/notification.wav"),
    fileName = "notification.wav",
)

// Explicit container
val aiffRecording = AudioRecording.fromBytes(
    bytes = aiffData,
    fileFormat = AudioFileFormat.Aiff,
)

// MP3 decode to PCM AudioRecording
val mp3Recording = AudioRecording.fromBytes(mp3Bytes, fileName = "clip.mp3")
```

Raw PCM (no container) uses a separate API:

```kotlin
val recording = AudioRecording.fromPcmBytes(format, pcmData)
```

### From a file path {id="from-file"}

```kotlin
val wavRecording = AudioRecording.fromFile(Path("recording.wav"))
val mp3Recording = AudioRecording.fromFile(Path("song.mp3"))
```

> Filesystem access is available on JVM, macOS, and iOS native targets. For Android and web, prefer `fromBytes()` or `fromSource()`.
>
{style="note"}

### From a stream {id="from-source"}

```kotlin
val source = contentResolver.openInputStream(uri)!!
    .asSource()
    .buffered()
val recording = AudioRecording.fromSource(source, fileName = "upload.mp3")
// or: AudioRecording.fromSource(source, AudioFileFormat.Wav)
```

## Metadata {id="metadata"}

See [Audio File Metadata](Metadata.md) for `AudioFileReader.readMetadata()` and `AudioTags`.

## Error handling {id="errors"}

### Read errors {id="read-errors"}

`AudioFileReadError.InvalidFile`
: The data is not a valid audio file (for example missing header or corrupt structure).

`AudioFileReadError.UnsupportedFormat`
: The format could not be detected, or the encoding is not supported (for example ADPCM, or MP3 encode).

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

`AudioFileWriteError.UnsupportedFormat`
: The audio format cannot be written to the target container (for example planar layout to WAV, or any MP3 encode).

`AudioFileWriteError.IO`
: A filesystem-level error occurred while writing.

## Platform guide {id="platforms"}

| Platform | Recommended method | Notes |
|----------|-------------------|-------|
| Compose Multiplatform | `fromBytes(Res.readBytes(...), fileName = ...)` | Works everywhere |
| JVM / Desktop | `fromFile(Path(...))` | Direct filesystem access |
| Android | `fromSource(...)` or `fromBytes(...)` | Use ContentResolver for user-selected files |
| iOS / macOS | `fromFile(Path(...))` or `fromBytes(...)` | Filesystem or bundled resources |
| Web (JS / Wasm) | `fromBytes(...)` | No filesystem; use fetch or bundled assets |

<seealso style="cards">
    <category ref="core-api">
        <a href="Metadata.md" summary="Read tags and technical info">Audio File Metadata</a>
        <a href="Recording.md" summary="Record audio from microphone">Recording</a>
        <a href="Playback.md" summary="Play back recordings">Playback</a>
        <a href="Audio-Format.md" summary="Audio format specifications">Audio Format</a>
        <a href="Migration.md" summary="Breaking API changes">Migration</a>
        <a href="Error-Handling.md" summary="Handle audio errors">Error Handling</a>
    </category>
</seealso>
