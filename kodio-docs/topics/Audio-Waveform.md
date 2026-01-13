[//]: # (title: AudioWaveform)

<show-structure for="chapter" depth="2"/>
<primary-label ref="compose"/>

<tldr>
<p><b>Waveform visualization</b>: Real-time audio amplitude display with customizable bars, colors, and gradients.</p>
</tldr>

`AudioWaveform` is a Compose component that visualizes audio amplitudes as animated bars—perfect for showing recording activity or audio levels.

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

The `amplitudes` parameter expects a `List<Float>` where each value is between 0.0 (silence) and 1.0 (maximum).

## Color customization {id="colors"}

<tabs>
<tab title="Solid color">

Use a single color for all bars:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    barColor = Color.Blue
)
```

</tab>
<tab title="Gradient">

Apply a gradient across the waveform:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    brush = Brush.horizontalGradient(
        colors = listOf(Color.Cyan, Color.Magenta)
    )
)
```

</tab>
<tab title="Theme color">

Use Material theme colors:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    barColor = MaterialTheme.colorScheme.primary
)
```

</tab>
</tabs>

## Bar configuration {id="bars"}

Customize the appearance of individual bars:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    barWidth = 4.dp,      // Width of each bar
    barSpacing = 2.dp,    // Gap between bars
    maxBars = 50          // Maximum number of bars shown
)
```

> The waveform automatically scrolls when `amplitudes` exceeds `maxBars`, showing the most recent values.
>
{style="tip"}

## Preset colors {id="presets"}

Kodio includes color presets for quick styling:

```kotlin
AudioWaveform(
    amplitudes = amplitudes,
    barColor = WaveformColors.Green        // Default
)

AudioWaveform(
    amplitudes = amplitudes,
    brush = WaveformColors.BlueGradient    // Gradient
)
```

### Available presets {id="preset-list"}

| Preset | Type | Description |
|--------|------|-------------|
| `WaveformColors.Green` | Solid | Classic green waveform |
| `WaveformColors.Blue` | Solid | Blue waveform |
| `WaveformColors.Purple` | Solid | Purple waveform |
| `WaveformColors.GreenGradient` | Gradient | Green to teal gradient |
| `WaveformColors.BlueGradient` | Gradient | Blue to purple gradient |

## Full example {id="example"}

A recording screen with styled waveform:

```kotlin
@Composable
fun WaveformDemo() {
    val recorderState = rememberRecorderState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Waveform container
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
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    barWidth = 3.dp,
                    barSpacing = 2.dp
                )
            } else {
                Text(
                    "🎤 Tap to record",
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
<def title="amplitudes: List<Float>">
Audio amplitude values between 0.0 and 1.0.
</def>
<def title="modifier: Modifier">
Standard Compose modifier for sizing and positioning.
</def>
<def title="barColor: Color">
Solid color for all bars. Ignored if <code>brush</code> is set.
</def>
<def title="brush: Brush">
Gradient or pattern brush for bars. Overrides <code>barColor</code>.
</def>
<def title="barWidth: Dp">
Width of each bar. Default: <code>3.dp</code>
</def>
<def title="barSpacing: Dp">
Gap between adjacent bars. Default: <code>2.dp</code>
</def>
<def title="maxBars: Int">
Maximum bars to display. Default: <code>50</code>
</def>
</deflist>

<seealso style="cards">
    <category ref="compose">
        <a href="Recorder-State.md" summary="Provides liveAmplitudes">RecorderState</a>
        <a href="Material3-Components.md" summary="Pre-built UI components">Material 3 Components</a>
    </category>
</seealso>
