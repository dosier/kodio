package space.kodio.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.kodio.compose.WaveformColors

/** Shared sample-app visual tokens (Playback is the north star). */
object SampleColors {
    val Accent = Color(0xFF7C5CDB)
    val AccentContainer = Color(0xFF2A2340)
    val OnAccentContainer = Color(0xFFE8E0FF)
    val Surface = Color(0xFF121218)
    val SurfaceVariant = Color(0xFF1C1C24)
    val Outline = Color(0xFF3A3A48)
    val OutlineVariant = Color(0xFF2A2A36)
    val ConfidenceHigh = Color(0xFF6BBF7A)
    val ConfidenceMid = Color(0xFFD4A84B)
    val ConfidenceLow = Color(0xFFD46A4A)
}

object SampleDimens {
    val WideBreakpoint = 840.dp
    val ScreenPaddingH = 16.dp
    val ScreenPaddingV = 12.dp
    val SectionGap = 12.dp
    val PanelPadding = 14.dp
    val PanelRadius = 10.dp
    val DenseGap = 8.dp
    val MetaKeyWidth = 110.dp
}

fun sampleWaveformColors(
    accent: Color = SampleColors.Accent,
): WaveformColors = WaveformColors(
    waveColor = SolidColor(accent.copy(alpha = 0.35f)),
    progressColor = SolidColor(accent),
    inactiveColor = SolidColor(accent.copy(alpha = 0.35f)),
)

@Composable
fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SampleColors.Accent,
            secondary = SampleColors.Accent,
            tertiary = SampleColors.Accent,
            primaryContainer = SampleColors.AccentContainer,
            onPrimaryContainer = SampleColors.OnAccentContainer,
            surface = SampleColors.Surface,
            surfaceVariant = SampleColors.SurfaceVariant,
            outline = SampleColors.Outline,
            outlineVariant = SampleColors.OutlineVariant,
        ),
        content = content,
    )
}

@Composable
fun SampleAdaptive(
    breakpoint: Dp = SampleDimens.WideBreakpoint,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        content(maxWidth >= breakpoint)
    }
}

@Composable
fun SampleScreenHeader(
    eyebrow: String = "Kodio",
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SamplePanel(
    modifier: Modifier = Modifier,
    fillMaxHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SampleDimens.PanelRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillMaxHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(SampleDimens.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(SampleDimens.DenseGap),
            content = content,
        )
    }
}

@Composable
fun SamplePanelLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun SampleErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun SampleMetaRow(
    key: String,
    value: String,
    keyWidth: Dp = SampleDimens.MetaKeyWidth,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(keyWidth),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SampleStatusText(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Composable
fun SampleStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    isError: Boolean = false,
) {
    val background = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(50),
        border = if (emphasized || isError) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = foreground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
