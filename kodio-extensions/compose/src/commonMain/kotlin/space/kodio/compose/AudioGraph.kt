package space.kodio.compose

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.map
import space.kodio.core.AudioFlow
import space.kodio.core.io.decode
import kotlin.math.absoluteValue


class AudioGraphTheme private constructor(
    val brush: Brush,
    val strokeWidth:  Float,
    val cap: StrokeCap,
    val pathEffect: PathEffect?,
    val alpha: Float,
    val colorFilter: ColorFilter?,
    val blendMode: BlendMode
) {
    companion object {
        @Composable
        fun default(
            brush: Brush = Brush.sweepGradient(
                colors = listOf(Color.Blue, Color.Green),
            ),
            strokeWidth:  Float = Stroke.HairlineWidth,
            cap: StrokeCap = Stroke.DefaultCap,
            pathEffect: PathEffect? = null,
            alpha: Float = 1.0f,
            colorFilter: ColorFilter? = null,
            blendMode: BlendMode = DefaultBlendMode
        ) = AudioGraphTheme(brush, strokeWidth, cap, pathEffect, alpha, colorFilter, blendMode)
    }
}

@Composable
fun AudioGraph(
    flow: AudioFlow,
    modifier: Modifier,
    theme: AudioGraphTheme = AudioGraphTheme.default()
) {
    AudioGraph(
        amplitudes = flow.collectAsRunningAmplitudeList(),
        theme = theme,
        modifier = modifier
    )
}
@Composable
internal fun AudioFlow.collectAsRunningAmplitudeList(): List<Double> {
    var amplitudes by remember { mutableStateOf(listOf<Double>()) }
    LaunchedEffect(this) {
        map { rawAudioDateFrame ->
            decode(rawAudioDateFrame, format)
        }.collect { samples ->
            val average = samples.maxOfOrNull { it.doubleValue(false) } ?:0.0
            amplitudes = amplitudes.plus(average).takeLast(100)
        }
    }
    return amplitudes
}

@Composable
fun AudioGraph(
    amplitudes: List<Double>,
    modifier: Modifier,
    theme: AudioGraphTheme = AudioGraphTheme.default()
) {
    AudioGraph(
        amplitudes = amplitudes,
        brush = theme.brush,
        modifier = modifier,
        strokeWidth = theme.strokeWidth,
        cap = theme.cap,
        pathEffect = theme.pathEffect,
        alpha = theme.alpha,
        colorFilter = theme.colorFilter,
        blendMode = theme.blendMode
    )
}

@Composable
fun AudioGraph(
    amplitudes: List<Double>,
    brush: Brush,
    modifier: Modifier = Modifier.fillMaxWidth(),
    strokeWidth: Float = Stroke.HairlineWidth,
    cap: StrokeCap = Stroke.DefaultCap,
    pathEffect: PathEffect? = null,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = DefaultBlendMode
) {
    Canvas(modifier) {
        val maxGapX = size.width / 100
        val minGapX = 5F
        val gapX = if (minGapX >= maxGapX) minGapX else maxGapX
        val maxSamples = (size.width / gapX).toInt()
        var x = 0F
        amplitudes.takeLast(maxSamples).forEach { amplitude ->
            val relativeAmplitude = (0.1 + (amplitude.absoluteValue * 10)).coerceIn(0.0, 1.0)
            val centerY = size.height / 2
            val startY = centerY - centerY * relativeAmplitude
            val endY = centerY + centerY * relativeAmplitude
            drawLine(
                brush = brush,
                start = Offset(x, startY.toFloat()),
                end = Offset(x, endY.toFloat()),
                strokeWidth = strokeWidth,
                cap = cap,
                pathEffect = pathEffect,
                alpha = alpha,
                colorFilter = colorFilter,
                blendMode = blendMode
            )
            x += gapX
        }
    }
}
