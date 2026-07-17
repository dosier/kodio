[//]: # (title: Audio File Metadata)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p>Read technical info and tags with <code>AudioFileReader.readMetadata()</code> without decoding the full PCM payload.</p>
</tldr>

## Overview {id="overview"}

```kotlin
val meta = AudioFileReader(Path("song.mp3")).readMetadata()
// or
val meta2 = AudioFileReader.readMetadata(bytes, fileName = "song.mp3")
```

`AudioFileMetadata` includes:

- `container`: `AudioFileFormat` (Wav, Aiff, Au, Mp3)
- `sampleRate`, `channels`
- `encoding`: `AudioEncodingInfo` (PCM or compressed)
- `duration`: exact for PCM containers; Xing/estimate for MP3
- `tags`: `AudioTags` (title, artist, album, and a `raw` map)

## Encoding info {id="encoding"}

```kotlin
when (val enc = meta.encoding) {
    is AudioEncodingInfo.Pcm -> {
        // enc.encoding is SampleEncoding (bit depth, endianness, layout)
    }
    is AudioEncodingInfo.Compressed.Mp3 -> {
        enc.bitrate
        enc.vbr
        enc.mpegVersion
        enc.layer
        enc.encoderDelay
        enc.encoderPadding
    }
}
```

`Compressed` is sealed. New codecs get their own subtypes when support lands.

## Tags by format {id="tags-by-format"}

### WAV

Technical fields come from the `fmt ` chunk. Tags come from a `LIST`/`INFO` chunk when present (`INAM` title, `IART` artist, `ICMT` comment, and so on). Unmapped INFO ids stay in `tags.raw`.

### AIFF

Technical fields come from `COMM`. Text chunks: `NAME` (title), `AUTH` (artist), `ANNO` (comment).

### AU

Header only. `tags` is `AudioTags.Empty`.

### MP3

Technical fields come from the first frame header plus Xing/VBRI when present. Tags: ID3v2 first, then ID3v1 fallback. Unmapped frames are kept in `tags.raw` (for example `TPE2`).

```kotlin
val mp3 = AudioFileReader(Path("song.mp3")).readMetadata()
println("${mp3.tags.artist} - ${mp3.tags.title}")
println(mp3.duration)
```

## Notes {id="notes"}

- Metadata parsing does not decode the full audio payload.
- Format resolution follows the same magic-then-extension rules as file loading. See [Audio File I/O](File-IO.md#detection).

<seealso style="cards">
    <category ref="core-api">
        <a href="File-IO.md" summary="Save and load audio files">Audio File I/O</a>
        <a href="API-Reference.md" summary="Public API summary">API Reference</a>
    </category>
</seealso>
