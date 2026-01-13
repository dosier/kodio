package space.kodio.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * A composable that displays an audio waveform visualization.
 * 
 * ## Example Usage
 * ```kotlin
 * @Composable
 * fun RecordingScreen() {
 *     val recorderState = rememberRecorderState()
 *     
 *     AudioWaveform(
 *         amplitudes = recorderState.liveAmplitudes,
 *         modifier = Modifier
 *             .fillMaxWidth()
 *             .height(64.dp)
 *     )
 * }
 * ```
 * 
 * @param amplitudes List of amplitude values (0.0 to 1.0)
 * @param modifier Modifier for the waveform
 * @param barColor Primary color for the waveform bars
 * @param barWidth Width of each bar
 * @param barSpacing Spacing between bars
 * @param maxBars Maximum number of bars to display
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF4CAF50),
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    maxBars: Int = 50
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        val totalBarWidth = barWidthPx + barSpacingPx
        
        val availableBars = (size.width / totalBarWidth).toInt().coerceAtMost(maxBars)
        val displayAmplitudes = amplitudes.takeLast(availableBars)
        
        val centerY = size.height / 2
        val maxHeight = size.height * 0.9f
        
        displayAmplitudes.forEachIndexed { index, amplitude ->
            val x = index * totalBarWidth + barWidthPx / 2
            val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
            val barHeight = (maxHeight * normalizedAmplitude).coerceAtLeast(2f)
            
            drawLine(
                color = barColor,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * A gradient-styled audio waveform visualization.
 * 
 * @param amplitudes List of amplitude values (0.0 to 1.0)
 * @param modifier Modifier for the waveform
 * @param brush Brush for the waveform gradient
 * @param barWidth Width of each bar
 * @param barSpacing Spacing between bars
 * @param maxBars Maximum number of bars to display
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    brush: Brush,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    maxBars: Int = 50
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        val totalBarWidth = barWidthPx + barSpacingPx
        
        val availableBars = (size.width / totalBarWidth).toInt().coerceAtMost(maxBars)
        val displayAmplitudes = amplitudes.takeLast(availableBars)
        
        val centerY = size.height / 2
        val maxHeight = size.height * 0.9f
        
        displayAmplitudes.forEachIndexed { index, amplitude ->
            val x = index * totalBarWidth + barWidthPx / 2
            val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
            val barHeight = (maxHeight * normalizedAmplitude).coerceAtLeast(2f)
            
            drawLine(
                brush = brush,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Pre-configured waveform colors for common use cases.
 */
object WaveformColors {
    val Green = Color(0xFF4CAF50)
    val Blue = Color(0xFF2196F3)
    val Red = Color(0xFFF44336)
    val Purple = Color(0xFF9C27B0)
    val Orange = Color(0xFFFF9800)
    
    val GreenGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
    )
    
    val BlueGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF2196F3), Color(0xFF03A9F4))
    )
    
    val PurpleGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF9C27B0), Color(0xFFE91E63))
    )
}


