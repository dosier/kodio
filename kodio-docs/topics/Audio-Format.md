[//]: # (title: Audio Format)

<show-structure for="chapter" depth="2"/>

The `AudioFormat` class defines technical specifications for audio data.

## AudioFormat {id="audio-format"}

```kotlin
data class AudioFormat(
    val sampleRate: Int,       // Hz
    val channels: Channels,    // Mono or Stereo
    val encoding: SampleEncoding
)
```

## Creating custom formats {id="custom"}

```kotlin
val format = AudioFormat(
    sampleRate = 48000,
    channels = Channels.Stereo,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
)
```

## Channels {id="channels"}

`Mono`
: Single channel (1)

`Stereo`
: Left and right (2)

## Bit depths {id="bit-depths"}

`Eight`
: 8-bit (48 dB range)

`Sixteen`
: 16-bit (96 dB range) - standard

`TwentyFour`
: 24-bit (144 dB range) - professional

`ThirtyTwo`
: 32-bit (192 dB range)

## Computed properties {id="computed" collapsible="true"}

`bytesPerSample`
: Bytes for one sample

`bytesPerFrame`
: Bytes for one frame (all channels)

<seealso style="cards">
    <category ref="core-api">
        <a href="Audio-Quality.md"/>
    </category>
</seealso>
