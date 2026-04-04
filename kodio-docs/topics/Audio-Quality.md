[//]: # (title: Audio Quality)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Quality presets</b>: Choose from Voice, Standard, High, or Lossless based on your use case.</p>
</tldr>

Kodio provides predefined quality presets optimized for common recording scenarios. Each preset balances sample rate, channels, and bit depth for its intended use case.

## Presets overview {id="presets"}

| Preset | Sample Rate | Channels | Bit Depth | Best For |
|--------|-------------|----------|-----------|----------|
| 🎤 `Voice` | 16 kHz | Mono | 16-bit | Speech, voice memos, transcription |
| 🎵 `Standard` | 44.1 kHz | Mono | 16-bit | General audio, podcasts (default) |
| 🎧 `High` | 48 kHz | Stereo | 16-bit | Music, professional content |
| 🎚️ `Lossless` | 96 kHz | Stereo | 24-bit | Studio recording, archival |

## Usage {id="usage"}

Pass a preset to any recording function:

```kotlin
// Voice memo
val voiceNote = Kodio.record(
    duration = 30.seconds,
    quality = AudioQuality.Voice
)

// Music recording
val music = Kodio.record(
    duration = 3.minutes,
    quality = AudioQuality.High
)

// Studio quality
val master = Kodio.record(
    duration = 5.minutes,
    quality = AudioQuality.Lossless
)
```

With a recorder:

```kotlin
val recorder = Kodio.recorder(quality = AudioQuality.Standard)
```

In Compose:

```kotlin
val recorderState = rememberRecorderState(
    quality = AudioQuality.Voice
)
```

## Choosing a preset {id="choosing"}

<deflist type="medium">
<def title="🎤 Voice">
<p><b>When to use</b>: Voice memos, speech-to-text input, phone calls, podcasts with speech only.</p>
<p><b>Trade-off</b>: Smallest files, optimized for speech frequencies (human voice range). Not suitable for music.</p>
</def>
<def title="🎵 Standard">
<p><b>When to use</b>: General-purpose recording, mixed content, compatibility with most playback systems.</p>
<p><b>Trade-off</b>: CD-quality sample rate (44.1 kHz), good balance of quality and file size. Default if not specified.</p>
</def>
<def title="🎧 High">
<p><b>When to use</b>: Music recording, professional podcasts, content for distribution.</p>
<p><b>Trade-off</b>: Stereo capture, higher sample rate. Larger files than Standard.</p>
</def>
<def title="🎚️ Lossless">
<p><b>When to use</b>: Studio recording, archival, post-processing workflows.</p>
<p><b>Trade-off</b>: Maximum quality, largest files (~35 MB/minute). Overkill for most applications.</p>
</def>
</deflist>

## File sizes {id="file-sizes"}

Approximate sizes for uncompressed WAV files:

| Preset | Per Minute | 5 Minutes | 30 Minutes |
|--------|------------|-----------|------------|
| Voice | ~2 MB | ~10 MB | ~60 MB |
| Standard | ~5 MB | ~25 MB | ~150 MB |
| High | ~11 MB | ~55 MB | ~330 MB |
| Lossless | ~35 MB | ~175 MB | ~1 GB |

> These are approximations for WAV format. Actual sizes may vary slightly based on audio content.
>
{style="note"}

## Platform negotiation {id="negotiation"}

Quality presets are forwarded to the platform's audio system as a *request*. If the exact format isn't supported, the platform gracefully falls back to the closest supported format:

| Platform | Negotiation behavior |
|----------|---------------------|
| Android | Tries the requested format first, falls back to 48 kHz/mono/16-bit if `AudioRecord` rejects it |
| JVM (JavaSound) | Checks mixer support, falls back to the device's default format |
| macOS (CoreAudio) | Tries AudioQueue with the requested format, falls back through device default and platform default |
| iOS (AVAudioEngine) | Converts from hardware format; falls back to 48 kHz/mono/16-bit if the target format isn't representable in AVAudioFormat (e.g., 24-bit int) |
| Web (AudioWorklet) | Sample rate is a hint to `getUserMedia`; output is always mono 16-bit PCM due to worklet constraints |

After recording starts, `Recorder.format` returns the actual negotiated format, which you can compare against the requested `quality.format`.

> If your application depends on a specific format (e.g., 16 kHz for speech recognition), check `recorder.format` after starting to confirm the platform honored it.
>
{style="note"}

## Custom formats {id="custom"}

Need something different? Create a custom `AudioFormat`:

```kotlin
val customFormat = AudioFormat(
    sampleRate = 22050,
    channels = Channels.Mono,
    encoding = SampleEncoding.PcmInt(IntBitDepth.Sixteen)
)
```

See [Custom Formats](Custom-Formats.md) for details.

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Recording with quality presets">Recording</a>
        <a href="Custom-Formats.md" summary="Create custom audio formats">Custom Formats</a>
        <a href="Audio-Format.md" summary="AudioFormat class reference">Audio Format</a>
    </category>
</seealso>
