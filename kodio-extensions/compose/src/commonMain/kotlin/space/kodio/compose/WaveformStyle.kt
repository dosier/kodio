package space.kodio.compose

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Defines how the waveform bars are aligned vertically within the component.
 */
enum class WaveformAlignment {
    /** Bars grow downward from the top edge. */
    Top,
    /** Bars grow outward from the center (default). */
    Center,
    /** Bars grow upward from the bottom edge. */
    Bottom
}

/**
 * Sealed class representing different waveform visualization styles.
 *
 * Each style can be customized with its own parameters to achieve various visual effects.
 *
 * ## Available Styles
 * - [Bar] - Classic vertical bars (default)
 * - [Spike] - Thin spikes like SoundCloud
 * - [Line] - Smooth connected line graph
 * - [Mirrored] - Symmetric bars above and below center
 * - [Filled] - Filled area under the waveform
 *
 * ## Example Usage
 * ```kotlin
 * // Default bar style
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     style = WaveformStyle.Bar()
 * )
 *
 * // SoundCloud-like spikes
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     style = WaveformStyle.Spike(width = 1.dp, spacing = 2.dp)
 * )
 *
 * // Mirrored for recording visualization
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     style = WaveformStyle.Mirrored()
 * )
 * ```
 */
sealed class WaveformStyle {

    /**
     * Classic vertical bar visualization.
     *
     * Renders amplitude values as vertical rounded rectangles.
     *
     * @param width Width of each bar
     * @param spacing Space between bars
     * @param cornerRadius Corner radius for bar caps (0 = square, higher = more rounded)
     * @param alignment Vertical alignment of bars
     * @param minHeight Minimum height for bars (prevents invisible zero-amplitude bars)
     */
    data class Bar(
        val width: Dp = 3.dp,
        val spacing: Dp = 2.dp,
        val cornerRadius: Dp = 1.5.dp,
        val alignment: WaveformAlignment = WaveformAlignment.Center,
        val minHeight: Dp = 2.dp,
    ) : WaveformStyle()

    /**
     * Thin spike visualization similar to SoundCloud.
     *
     * Renders amplitude values as thin vertical lines.
     *
     * @param width Width of each spike
     * @param spacing Space between spikes
     * @param cap Stroke cap style for spike ends
     * @param alignment Vertical alignment of spikes
     */
    data class Spike(
        val width: Dp = 2.dp,
        val spacing: Dp = 1.dp,
        val cap: StrokeCap = StrokeCap.Round,
        val alignment: WaveformAlignment = WaveformAlignment.Center,
    ) : WaveformStyle()

    /**
     * Smooth connected line visualization.
     *
     * Renders amplitude values as a continuous line with optional Bezier smoothing.
     *
     * @param strokeWidth Width of the line
     * @param smoothing Bezier curve smoothing factor (0 = sharp corners, 1 = maximum smoothing)
     * @param cap Stroke cap style for line ends
     */
    data class Line(
        val strokeWidth: Dp = 2.dp,
        val smoothing: Float = 0.5f,
        val cap: StrokeCap = StrokeCap.Round,
    ) : WaveformStyle()

    /**
     * Mirrored bar visualization.
     *
     * Renders amplitude values as bars that extend both above and below the center line,
     * creating a symmetric waveform effect. Ideal for recording visualizations.
     *
     * @param barWidth Width of each bar
     * @param barSpacing Space between bars
     * @param gap Gap between the top and bottom halves at the center
     * @param cornerRadius Corner radius for bar caps
     */
    data class Mirrored(
        val barWidth: Dp = 3.dp,
        val barSpacing: Dp = 2.dp,
        val gap: Dp = 2.dp,
        val cornerRadius: Dp = 1.5.dp,
    ) : WaveformStyle()

    /**
     * Filled area visualization.
     *
     * Renders amplitude values as a filled area under a smooth curve,
     * similar to an area chart.
     *
     * @param smoothing Bezier curve smoothing factor (0 = sharp corners, 1 = maximum smoothing)
     * @param alignment Vertical alignment (Top = fills downward, Bottom = fills upward)
     */
    data class Filled(
        val smoothing: Float = 0.3f,
        val alignment: WaveformAlignment = WaveformAlignment.Bottom,
    ) : WaveformStyle()
}
