[//]: # (title: Custom Formats)

<show-structure for="chapter" depth="2"/>

Create custom audio formats beyond the built-in presets for specialized requirements.

> For most use cases, [AudioQuality presets](Audio-Quality.md) are sufficient.
>
{style="note"}

## Custom format {id="custom"}

```kotlin
val format = AudioFormat(
    sampleRate = 22050,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
)
```

## Using custom formats {id="usage"}

```kotlin
val recording = AudioRecording.fromBytes(
    format = customFormat,
    data = rawPcmBytes
)

val recording = AudioRecording.fromChunks(
    format = customFormat,
    chunks = listOf(chunk1, chunk2)
)
```

## Common sample rates {id="sample-rates" collapsible="true"}

| Rate | Use case |
|------|----------|
| 8,000 Hz | Telephone |
| 16,000 Hz | Voice (AudioQuality.Voice) |
| 44,100 Hz | CD quality (AudioQuality.Standard) |
| 48,000 Hz | Professional (AudioQuality.High) |
| 96,000 Hz | Studio (AudioQuality.Lossless) |

## Bit depths {id="bit-depths" collapsible="true"}

`8-bit`
: 48 dB range, smallest files

`16-bit`
: 96 dB range, CD standard

`24-bit`
: 144 dB range, professional

`32-bit`
: 192 dB range, processing

## Calculated properties {id="calculated"}

```kotlin
format.bytesPerSample  // Bytes per sample (one channel)
format.bytesPerFrame   // Bytes per frame (all channels)
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Audio-Quality.md"/>
        <a href="Audio-Format.md"/>
    </category>
</seealso>
