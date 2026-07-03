[//]: # (title: AudioWaveform)

<show-structure for="chapter" depth="2"/>
<primary-label ref="compose"/>

<tldr>
<p><b>Waveform visualization</b>: Real-time audio amplitude display with multiple styles, colors, and optional playback progress.</p>
</tldr>

`AudioWaveform` is a Compose component that visualizes audio amplitudes. Use it during recording (via `RecorderState.liveAmplitudes`) or to show a static waveform with playback progress.

## Basic usage {id="usage"}

Connect to `RecorderState.liveAmplitudes` for real-time visualization during recording:

```kotlin
val recorderState = rememberRecorderState()

if (recorderState.isRecording) {
    AudioWaveform(
        amplitudes = recorderState.liveAmplitudes,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    )
}
```

The `amplitudes` parameter expects a `List<Float>` where each value is between 0.0 (silence) and 1.0 (maximum). Values outside that range are clamped.

## Waveform styles {id="styles"}

Pass a `WaveformStyle` to change how amplitudes are drawn. The modifier must include size constraints (for example `fillMaxWidth()` and `height()`).

<tabs>
<tab title="Bar (default)">

Classic vertical bars:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    style = WaveformStyle.Bar(
        width = 3.dp,
        spacing = 2.dp,
        cornerRadius = 1.5.dp,
        alignment = WaveformAlignment.Center,
        minHeight = 2.dp,
    ),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
<tab title="Spike">

Thin spikes, similar to SoundCloud:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    style = WaveformStyle.Spike(
        width = 2.dp,
        spacing = 1.dp,
    ),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
<tab title="Line">

Smooth connected line:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    style = WaveformStyle.Line(
        strokeWidth = 2.dp,
        smoothing = 0.5f,
    ),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
<tab title="Mirrored">

Symmetric bars above and below center. Useful for live recording:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    style = WaveformStyle.Mirrored(
        barWidth = 4.dp,
        gap = 4.dp,
    ),
    modifier = Modifier.fillMaxWidth().height(80.dp)
)
```

</tab>
<tab title="Filled">

Filled area under a smooth curve:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    style = WaveformStyle.Filled(
        smoothing = 0.3f,
        alignment = WaveformAlignment.Bottom,
    ),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
</tabs>

## Color customization {id="colors"}

Colors are configured with `WaveformColors`, not individual `Color` parameters on `AudioWaveform`.

<tabs>
<tab title="Solid color">

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    colors = WaveformColors.solidColor(Color.Blue),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
<tab title="Gradient">

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    colors = WaveformColors.gradient(
        colors = listOf(Color.Cyan, Color.Magenta)
    ),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
<tab title="Theme color">

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    colors = WaveformColors.solidColor(MaterialTheme.colorScheme.primary),
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

</tab>
</tabs>

### Preset colors {id="presets"}

| Preset | Type | Description |
|--------|------|-------------|
| `WaveformColors.Green` | Solid | Default green theme |
| `WaveformColors.Blue` | Solid | Blue |
| `WaveformColors.Red` | Solid | Red |
| `WaveformColors.Purple` | Solid | Purple |
| `WaveformColors.Orange` | Solid | Orange |
| `WaveformColors.Cyan` | Solid | Cyan |
| `WaveformColors.GreenGradient` | Gradient | Green to light green |
| `WaveformColors.BlueGradient` | Gradient | Blue to cyan |
| `WaveformColors.PurpleGradient` | Gradient | Purple to pink |
| `WaveformColors.SunsetGradient` | Gradient | Orange to yellow |
| `WaveformColors.OceanGradient` | Gradient | Deep teal to light cyan |
| `WaveformColors.NeonGradient` | Gradient | Magenta to cyan |

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    colors = WaveformColors.PurpleGradient,
    modifier = Modifier.fillMaxWidth().height(64.dp)
)
```

## Playback progress {id="progress"}

When `progress` is less than `1f`, bars before the progress point use `progressColor` (or `waveColor` if unset). Pass `onProgressChange` to enable tap and drag seeking:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    progress = playbackProgress,
    onProgressChange = { newProgress -> seekTo(newProgress) },
    colors = WaveformColors(
        waveColor = SolidColor(Color.Gray),
        progressColor = SolidColor(Color.Green),
    ),
    modifier = Modifier.fillMaxWidth().height(48.dp)
)
```

Set `animate = false` to snap amplitudes without animation.

## Full example {id="example"}

A recording screen with a mirrored waveform:

```kotlin
@Composable
fun WaveformDemo() {
    val recorderState = rememberRecorderState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (recorderState.isRecording) {
                AudioWaveform(
                    amplitudes = recorderState.liveAmplitudes,
                    modifier = Modifier.fillMaxSize(),
                    style = WaveformStyle.Mirrored(),
                    colors = WaveformColors.gradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                )
            } else {
                Text(
                    "Tap to record",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = { recorderState.toggle() }) {
            Text(if (recorderState.isRecording) "Stop" else "Record")
        }
    }
}
```

## Parameters reference {id="parameters"}

<deflist type="medium">
<def title="amplitudes: List&lt;Float&gt;">
Audio amplitude values between 0.0 and 1.0.
</def>
<def title="modifier: Modifier">
Compose modifier. Must include size constraints (for example <code>fillMaxWidth()</code> and <code>height()</code>).
</def>
<def title="style: WaveformStyle">
Visualization style. Default: <code>WaveformStyle.Bar()</code>.
</def>
<def title="colors: WaveformColors">
Color configuration. Default: <code>WaveformColors.default()</code>.
</def>
<def title="progress: Float">
Playback progress from 0.0 to 1.0. Default: <code>1f</code> (fully played).
</def>
<def title="onProgressChange: ((Float) -&gt; Unit)?">
Callback when the user seeks via tap or drag. Pass <code>null</code> to disable interaction.
</def>
<def title="animate: Boolean">
Whether amplitude changes animate. Default: <code>true</code>.
</def>
<def title="animationSpec: AnimationSpec&lt;Float&gt;">
Animation spec for amplitude transitions when <code>animate</code> is true.
</def>
</deflist>

<seealso style="cards">
    <category ref="compose">
        <a href="Recorder-State.md" summary="Provides liveAmplitudes">RecorderState</a>
        <a href="Material3-Components.md" summary="Pre-built UI components">Material 3 Components</a>
    </category>
</seealso>
