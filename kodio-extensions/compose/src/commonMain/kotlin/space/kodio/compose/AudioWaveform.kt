package space.kodio.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import space.kodio.compose.internal.renderBarStyle
import space.kodio.compose.internal.renderFilledStyle
import space.kodio.compose.internal.renderLineStyle
import space.kodio.compose.internal.renderMirroredStyle
import space.kodio.compose.internal.renderSpikeStyle

/**
 * A highly customizable audio waveform visualization composable.
 *
 * Supports multiple visualization styles, smooth animations, playback progress tracking,
 * and interactive seeking.
 *
 * ## Basic Usage
 * ```kotlin
 * AudioWaveform(
 *     amplitudes = recorderState.liveAmplitudes,
 *     modifier = Modifier.fillMaxWidth().height(64.dp)
 * )
 * ```
 *
 * ## With Custom Style
 * ```kotlin
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     style = WaveformStyle.Mirrored(barWidth = 4.dp, gap = 4.dp),
 *     colors = WaveformColors.PurpleGradient,
 *     modifier = Modifier.fillMaxWidth().height(80.dp)
 * )
 * ```
 *
 * ## With Playback Progress
 * ```kotlin
 * AudioWaveform(
 *     amplitudes = amplitudes,
 *     progress = playbackProgress,
 *     onProgressChange = { newProgress -> seekTo(newProgress) },
 *     colors = WaveformColors(
 *         waveColor = SolidColor(Color.Gray),
 *         progressColor = SolidColor(Color.Green),
 *     ),
 *     modifier = Modifier.fillMaxWidth().height(48.dp)
 * )
 * ```
 *
 * ## Available Styles
 * - [WaveformStyle.Bar] - Classic vertical bars (default)
 * - [WaveformStyle.Spike] - Thin spikes like SoundCloud
 * - [WaveformStyle.Line] - Smooth connected line graph
 * - [WaveformStyle.Mirrored] - Symmetric bars (ideal for recording)
 * - [WaveformStyle.Filled] - Filled area under the curve
 *
 * @param amplitudes List of amplitude values (0.0 to 1.0). Values outside this range are clamped.
 * @param modifier Modifier for the waveform. **Must include size constraints** (e.g., `fillMaxWidth()`, `height()`).
 * @param style Visualization style. Defaults to [WaveformStyle.Bar].
 * @param colors Color configuration. Defaults to [WaveformColors.default].
 * @param progress Playback progress (0.0 to 1.0). Bars before this point use `progressColor`.
 * @param onProgressChange Callback when user seeks via tap/drag. Pass `null` to disable interaction.
 * @param animate Whether to animate amplitude changes. Defaults to `true`.
 * @param animationSpec Animation specification for amplitude transitions.
 *
 * @see WaveformStyle
 * @see WaveformColors
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    style: WaveformStyle = WaveformStyle.Bar(),
    colors: WaveformColors = WaveformColors.default(),
    progress: Float = 1f,
    onProgressChange: ((Float) -> Unit)? = null,
    animate: Boolean = true,
    animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    ),
) {
    // Animated amplitudes state
    val animatedAmplitudes = remember { mutableStateListOf<Animatable<Float, *>>() }

    // Keep callbacks up to date
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)

    // Update animated amplitudes when input changes
    LaunchedEffect(amplitudes.size) {
        // Resize the list to match input
        while (animatedAmplitudes.size > amplitudes.size) {
            animatedAmplitudes.removeLast()
        }
        while (animatedAmplitudes.size < amplitudes.size) {
            animatedAmplitudes.add(Animatable(0f))
        }
    }

    // Animate each amplitude value
    LaunchedEffect(amplitudes) {
        // IMPORTANT: animateTo() is a suspend function. If we call it sequentially for many bars,
        // live updates will backlog and the waveform will appear delayed. Launch per-bar so updates
        // happen concurrently; this LaunchedEffect will also cancel in-flight animations on new data.
        amplitudes.forEachIndexed { index, targetValue ->
            if (index >= animatedAmplitudes.size) return@forEachIndexed
            val animatable = animatedAmplitudes[index]
            val clampedTarget = targetValue.coerceIn(0f, 1f)
            launch {
                if (animate) {
                    animatable.animateTo(clampedTarget, animationSpec)
                } else {
                    animatable.snapTo(clampedTarget)
                }
            }
        }
    }

    // Get current amplitude values
    val currentAmplitudes = if (animate && animatedAmplitudes.isNotEmpty()) {
        animatedAmplitudes.map { it.value }
    } else {
        amplitudes
    }

    // Handle interaction
    val interactionModifier = if (onProgressChange != null) {
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnProgressChange?.invoke(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    currentOnProgressChange?.invoke(newProgress)
                }
            }
    } else {
        modifier
    }

    // Get brushes from colors
    val waveBrush = colors.effectiveInactiveColor
    val progressBrush = colors.effectiveProgressColor

    Canvas(
        modifier = interactionModifier
            .background(colors.backgroundColor)
    ) {
        when (style) {
            is WaveformStyle.Bar -> renderBarStyle(
                amplitudes = currentAmplitudes,
                style = style,
                brush = waveBrush,
                progressBrush = progressBrush,
                progress = progress
            )

            is WaveformStyle.Spike -> renderSpikeStyle(
                amplitudes = currentAmplitudes,
                style = style,
                brush = waveBrush,
                progressBrush = progressBrush,
                progress = progress
            )

            is WaveformStyle.Line -> renderLineStyle(
                amplitudes = currentAmplitudes,
                style = style,
                brush = waveBrush,
                progressBrush = progressBrush,
                progress = progress
            )

            is WaveformStyle.Mirrored -> renderMirroredStyle(
                amplitudes = currentAmplitudes,
                style = style,
                brush = waveBrush,
                progressBrush = progressBrush,
                progress = progress
            )

            is WaveformStyle.Filled -> renderFilledStyle(
                amplitudes = currentAmplitudes,
                style = style,
                brush = waveBrush,
                progressBrush = progressBrush,
                progress = progress
            )
        }
    }
}

/**
 * Simplified overload for basic waveform display without progress tracking.
 *
 * @param amplitudes List of amplitude values (0.0 to 1.0)
 * @param modifier Modifier for the waveform
 * @param style Visualization style
 * @param colors Color configuration
 * @param animate Whether to animate amplitude changes
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    style: WaveformStyle = WaveformStyle.Bar(),
    colors: WaveformColors = WaveformColors.default(),
    animate: Boolean = true,
) {
    AudioWaveform(
        amplitudes = amplitudes,
        modifier = modifier,
        style = style,
        colors = colors,
        progress = 1f,
        onProgressChange = null,
        animate = animate,
    )
}
