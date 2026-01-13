package space.kodio.compose.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import space.kodio.compose.WaveformAlignment
import space.kodio.compose.WaveformStyle

/**
 * Internal renderer functions for different waveform visualization styles.
 * These functions draw directly onto a Canvas DrawScope.
 */

// ============================================================================
// Bar Style Renderer
// ============================================================================

/**
 * Renders the waveform as vertical bars.
 */
internal fun DrawScope.renderBarStyle(
    amplitudes: List<Float>,
    style: WaveformStyle.Bar,
    brush: Brush,
    progressBrush: Brush,
    progress: Float,
) {
    if (amplitudes.isEmpty()) return

    val barWidthPx = style.width.toPx()
    val barSpacingPx = style.spacing.toPx()
    val totalBarWidth = barWidthPx + barSpacingPx
    val minHeightPx = style.minHeight.toPx()
    val cornerRadiusPx = style.cornerRadius.toPx()

    val maxBars = (size.width / totalBarWidth).toInt()
    val displayAmplitudes = if (amplitudes.size > maxBars) {
        amplitudes.takeLast(maxBars)
    } else {
        amplitudes
    }

    val maxHeight = size.height * 0.95f
    val progressIndex = (displayAmplitudes.size * progress).toInt()

    displayAmplitudes.forEachIndexed { index, amplitude ->
        val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
        val barHeight = (maxHeight * normalizedAmplitude).coerceAtLeast(minHeightPx)
        val x = index * totalBarWidth

        val (top, bottom) = calculateBarPosition(
            alignment = style.alignment,
            barHeight = barHeight,
            canvasHeight = size.height
        )

        val currentBrush = if (index < progressIndex) progressBrush else brush

        if (cornerRadiusPx > 0) {
            drawRoundRect(
                brush = currentBrush,
                topLeft = Offset(x, top),
                size = Size(barWidthPx, bottom - top),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        } else {
            drawRect(
                brush = currentBrush,
                topLeft = Offset(x, top),
                size = Size(barWidthPx, bottom - top)
            )
        }
    }
}

// ============================================================================
// Spike Style Renderer
// ============================================================================

/**
 * Renders the waveform as thin spikes (SoundCloud style).
 */
internal fun DrawScope.renderSpikeStyle(
    amplitudes: List<Float>,
    style: WaveformStyle.Spike,
    brush: Brush,
    progressBrush: Brush,
    progress: Float,
) {
    if (amplitudes.isEmpty()) return

    val spikeWidthPx = style.width.toPx()
    val spikeSpacingPx = style.spacing.toPx()
    val totalWidth = spikeWidthPx + spikeSpacingPx

    val maxSpikes = (size.width / totalWidth).toInt()
    val displayAmplitudes = if (amplitudes.size > maxSpikes) {
        amplitudes.takeLast(maxSpikes)
    } else {
        amplitudes
    }

    val maxHeight = size.height * 0.95f
    val progressIndex = (displayAmplitudes.size * progress).toInt()

    displayAmplitudes.forEachIndexed { index, amplitude ->
        val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
        val spikeHeight = (maxHeight * normalizedAmplitude).coerceAtLeast(2f)
        val x = index * totalWidth + spikeWidthPx / 2

        val (top, bottom) = calculateBarPosition(
            alignment = style.alignment,
            barHeight = spikeHeight,
            canvasHeight = size.height
        )

        val currentBrush = if (index < progressIndex) progressBrush else brush

        drawLine(
            brush = currentBrush,
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = spikeWidthPx,
            cap = style.cap
        )
    }
}

// ============================================================================
// Line Style Renderer
// ============================================================================

/**
 * Renders the waveform as a smooth connected line.
 */
internal fun DrawScope.renderLineStyle(
    amplitudes: List<Float>,
    style: WaveformStyle.Line,
    brush: Brush,
    progressBrush: Brush,
    progress: Float,
) {
    if (amplitudes.size < 2) return

    val strokeWidthPx = style.strokeWidth.toPx()
    val maxHeight = size.height * 0.9f
    val centerY = size.height / 2

    // Calculate points
    val points = amplitudes.mapIndexed { index, amplitude ->
        val x = (index.toFloat() / (amplitudes.size - 1).coerceAtLeast(1)) * size.width
        val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
        val y = centerY - (normalizedAmplitude * maxHeight / 2) + (maxHeight / 4)
        Offset(x, y)
    }

    // Create path with smoothing
    val path = createSmoothPath(points, style.smoothing)

    // Draw progress portion
    val progressX = size.width * progress
    
    // Draw the full line with inactive color first, then overlay with progress color
    drawPath(
        path = path,
        brush = brush,
        style = Stroke(width = strokeWidthPx, cap = style.cap)
    )

    if (progress < 1f && progress > 0f) {
        // Clip and draw progress portion
        val progressPath = createSmoothPath(
            points.filter { it.x <= progressX },
            style.smoothing
        )
        drawPath(
            path = progressPath,
            brush = progressBrush,
            style = Stroke(width = strokeWidthPx, cap = style.cap)
        )
    }
}

// ============================================================================
// Mirrored Style Renderer
// ============================================================================

/**
 * Renders the waveform as mirrored bars (symmetric above and below center).
 */
internal fun DrawScope.renderMirroredStyle(
    amplitudes: List<Float>,
    style: WaveformStyle.Mirrored,
    brush: Brush,
    progressBrush: Brush,
    progress: Float,
) {
    if (amplitudes.isEmpty()) return

    val barWidthPx = style.barWidth.toPx()
    val barSpacingPx = style.barSpacing.toPx()
    val gapPx = style.gap.toPx()
    val cornerRadiusPx = style.cornerRadius.toPx()
    val totalBarWidth = barWidthPx + barSpacingPx

    val maxBars = (size.width / totalBarWidth).toInt()
    val displayAmplitudes = if (amplitudes.size > maxBars) {
        amplitudes.takeLast(maxBars)
    } else {
        amplitudes
    }

    val centerY = size.height / 2
    val maxBarHeight = (size.height - gapPx) / 2 * 0.95f
    val progressIndex = (displayAmplitudes.size * progress).toInt()

    displayAmplitudes.forEachIndexed { index, amplitude ->
        val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
        val barHeight = (maxBarHeight * normalizedAmplitude).coerceAtLeast(2f)
        val x = index * totalBarWidth

        val currentBrush = if (index < progressIndex) progressBrush else brush

        // Top bar (grows upward from center)
        val topBarTop = centerY - gapPx / 2 - barHeight
        val topBarBottom = centerY - gapPx / 2

        // Bottom bar (grows downward from center)
        val bottomBarTop = centerY + gapPx / 2
        val bottomBarBottom = centerY + gapPx / 2 + barHeight

        if (cornerRadiusPx > 0) {
            // Top bar
            drawRoundRect(
                brush = currentBrush,
                topLeft = Offset(x, topBarTop),
                size = Size(barWidthPx, topBarBottom - topBarTop),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
            // Bottom bar
            drawRoundRect(
                brush = currentBrush,
                topLeft = Offset(x, bottomBarTop),
                size = Size(barWidthPx, bottomBarBottom - bottomBarTop),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        } else {
            // Top bar
            drawRect(
                brush = currentBrush,
                topLeft = Offset(x, topBarTop),
                size = Size(barWidthPx, topBarBottom - topBarTop)
            )
            // Bottom bar
            drawRect(
                brush = currentBrush,
                topLeft = Offset(x, bottomBarTop),
                size = Size(barWidthPx, bottomBarBottom - bottomBarTop)
            )
        }
    }
}

// ============================================================================
// Filled Style Renderer
// ============================================================================

/**
 * Renders the waveform as a filled area under a smooth curve.
 */
internal fun DrawScope.renderFilledStyle(
    amplitudes: List<Float>,
    style: WaveformStyle.Filled,
    brush: Brush,
    progressBrush: Brush,
    progress: Float,
) {
    if (amplitudes.size < 2) return

    val maxHeight = size.height * 0.95f

    val baseY = when (style.alignment) {
        WaveformAlignment.Top -> 0f
        WaveformAlignment.Center -> size.height / 2
        WaveformAlignment.Bottom -> size.height
    }

    val direction = when (style.alignment) {
        WaveformAlignment.Top -> 1f    // Grow downward
        WaveformAlignment.Center -> -1f // Grow upward from center
        WaveformAlignment.Bottom -> -1f // Grow upward
    }

    // Calculate points
    val points = amplitudes.mapIndexed { index, amplitude ->
        val x = (index.toFloat() / (amplitudes.size - 1).coerceAtLeast(1)) * size.width
        val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
        val y = baseY + (direction * normalizedAmplitude * maxHeight)
        Offset(x, y)
    }

    // Create filled path
    val path = Path().apply {
        // Start at baseline
        moveTo(0f, baseY)

        // Add smooth curve through points
        if (style.smoothing > 0f && points.size > 2) {
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val controlX = (p0.x + p1.x) / 2
                quadraticTo(
                    p0.x + (controlX - p0.x) * style.smoothing,
                    p0.y,
                    controlX,
                    (p0.y + p1.y) / 2
                )
                quadraticTo(
                    controlX + (p1.x - controlX) * style.smoothing,
                    p1.y,
                    p1.x,
                    p1.y
                )
            }
        } else {
            points.forEach { point ->
                lineTo(point.x, point.y)
            }
        }

        // Close path back to baseline
        lineTo(size.width, baseY)
        close()
    }

    // Draw filled area
    drawPath(
        path = path,
        brush = if (progress >= 1f) progressBrush else brush,
        style = Fill
    )

    // Draw progress overlay if needed
    if (progress < 1f && progress > 0f) {
        val progressX = size.width * progress
        val progressPoints = points.filter { it.x <= progressX }
        
        if (progressPoints.isNotEmpty()) {
            val progressPath = Path().apply {
                moveTo(0f, baseY)
                progressPoints.forEach { point ->
                    lineTo(point.x, point.y)
                }
                lineTo(progressX, baseY)
                close()
            }
            drawPath(
                path = progressPath,
                brush = progressBrush,
                style = Fill
            )
        }
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Calculates the top and bottom Y coordinates for a bar based on alignment.
 */
private fun calculateBarPosition(
    alignment: WaveformAlignment,
    barHeight: Float,
    canvasHeight: Float
): Pair<Float, Float> {
    return when (alignment) {
        WaveformAlignment.Top -> {
            0f to barHeight
        }
        WaveformAlignment.Center -> {
            val centerY = canvasHeight / 2
            (centerY - barHeight / 2) to (centerY + barHeight / 2)
        }
        WaveformAlignment.Bottom -> {
            (canvasHeight - barHeight) to canvasHeight
        }
    }
}

/**
 * Creates a smooth path through the given points using Bezier curves.
 */
private fun createSmoothPath(points: List<Offset>, smoothing: Float): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        
        moveTo(points.first().x, points.first().y)
        
        if (points.size == 1) return@apply
        
        if (smoothing <= 0f || points.size == 2) {
            // No smoothing - straight lines
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        } else {
            // Bezier curve smoothing
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                
                val controlX1 = p0.x + (p1.x - p0.x) * smoothing
                val controlX2 = p1.x - (p1.x - p0.x) * smoothing
                
                cubicTo(
                    controlX1, p0.y,
                    controlX2, p1.y,
                    p1.x, p1.y
                )
            }
        }
    }
}
