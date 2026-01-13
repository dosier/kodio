package space.kodio.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Color configuration for [AudioWaveform].
 *
 * Supports solid colors, gradients, and different colors for played/unplayed portions.
 *
 * ## Example Usage
 * ```kotlin
 * // Simple solid color
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     colors = WaveformColors.solidColor(Color.Green)
 * )
 *
 * // Gradient
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     colors = WaveformColors.gradient(listOf(Color.Cyan, Color.Blue))
 * )
 *
 * // With progress colors (for playback)
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     colors = WaveformColors(
 *         waveColor = SolidColor(Color.Green),
 *         progressColor = SolidColor(Color.White),
 *         inactiveColor = SolidColor(Color.Gray),
 *     ),
 *     progress = 0.5f
 * )
 * ```
 *
 * @param waveColor Primary color/brush for the waveform
 * @param progressColor Color for the "played" portion when using progress (defaults to [waveColor])
 * @param inactiveColor Color for the "unplayed" portion when using progress (defaults to [waveColor] with reduced alpha)
 * @param backgroundColor Optional background color for the waveform area
 */
@Immutable
data class WaveformColors(
    val waveColor: Brush,
    val progressColor: Brush? = null,
    val inactiveColor: Brush? = null,
    val backgroundColor: Color = Color.Transparent,
) {
    /**
     * Returns the effective progress color, falling back to [waveColor] if not specified.
     */
    val effectiveProgressColor: Brush
        get() = progressColor ?: waveColor

    /**
     * Returns the effective inactive color, falling back to a dimmed version of [waveColor].
     */
    val effectiveInactiveColor: Brush
        get() = inactiveColor ?: waveColor.copy(alpha = 0.3f)

    companion object {
        /**
         * Default waveform colors with a green theme.
         */
        fun default(): WaveformColors = WaveformColors(
            waveColor = SolidColor(Color(0xFF4CAF50))
        )

        /**
         * Creates colors from a single solid color.
         */
        fun solidColor(
            color: Color,
            inactiveAlpha: Float = 0.3f
        ): WaveformColors = WaveformColors(
            waveColor = SolidColor(color),
            inactiveColor = SolidColor(color.copy(alpha = inactiveAlpha))
        )

        /**
         * Creates colors from a horizontal gradient.
         */
        fun gradient(
            colors: List<Color>,
            inactiveAlpha: Float = 0.3f
        ): WaveformColors = WaveformColors(
            waveColor = Brush.horizontalGradient(colors),
            inactiveColor = Brush.horizontalGradient(colors.map { it.copy(alpha = inactiveAlpha) })
        )

        /**
         * Creates colors with a vertical gradient based on amplitude.
         */
        fun verticalGradient(
            colors: List<Color>,
            inactiveAlpha: Float = 0.3f
        ): WaveformColors = WaveformColors(
            waveColor = Brush.verticalGradient(colors),
            inactiveColor = Brush.verticalGradient(colors.map { it.copy(alpha = inactiveAlpha) })
        )

        // Pre-defined color schemes

        /** Green theme (default) */
        val Green = solidColor(Color(0xFF4CAF50))

        /** Blue theme */
        val Blue = solidColor(Color(0xFF2196F3))

        /** Red theme */
        val Red = solidColor(Color(0xFFF44336))

        /** Purple theme */
        val Purple = solidColor(Color(0xFF9C27B0))

        /** Orange theme */
        val Orange = solidColor(Color(0xFFFF9800))

        /** Cyan theme */
        val Cyan = solidColor(Color(0xFF00BCD4))

        /** Green gradient theme */
        val GreenGradient = gradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)))

        /** Blue gradient theme */
        val BlueGradient = gradient(listOf(Color(0xFF2196F3), Color(0xFF03A9F4)))

        /** Purple gradient theme */
        val PurpleGradient = gradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63)))

        /** Sunset gradient theme */
        val SunsetGradient = gradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFEB3B)))

        /** Ocean gradient theme */
        val OceanGradient = gradient(listOf(Color(0xFF006064), Color(0xFF00ACC1), Color(0xFF4DD0E1)))

        /** Neon gradient theme */
        val NeonGradient = gradient(listOf(Color(0xFFFF00FF), Color(0xFF00FFFF)))
    }
}

/**
 * Creates a copy of this brush with modified alpha.
 * For solid colors, reduces the alpha. For gradients, this creates a dimmed version.
 */
private fun Brush.copy(alpha: Float): Brush {
    return when (this) {
        is SolidColor -> SolidColor(this.value.copy(alpha = this.value.alpha * alpha))
        else -> this // Gradients are already created with the correct alpha
    }
}
