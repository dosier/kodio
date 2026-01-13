package space.kodio.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import space.kodio.compose.AudioWaveform
import space.kodio.compose.RecorderState
import space.kodio.compose.WaveformAlignment
import space.kodio.compose.WaveformColors
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberRecorderState
import kotlin.math.sin
import kotlin.random.Random

/**
 * Showcase demonstrating all AudioWaveform visualization styles and features.
 */
@Composable
@Preview
fun WaveformShowcase() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Styles Gallery", "Live Recording")

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                // Tab Content
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize()
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> StylesGalleryTab()
                        1 -> LiveRecordingTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun StylesGalleryTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "Waveform Styles Gallery",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Demonstrating all AudioWaveform visualization styles and features",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Bar Style Variations
        item { BarStyleShowcase() }

        // Spike Style
        item { SpikeStyleShowcase() }

        // Line Style
        item { LineStyleShowcase() }

        // Mirrored Style
        item { MirroredStyleShowcase() }

        // Filled Style
        item { FilledStyleShowcase() }

        // Playback Progress Demo
        item { PlaybackProgressShowcase() }

        // Live Animation Demo
        item { LiveAnimationShowcase() }

        // Color Themes Demo
        item { ColorThemesShowcase() }

        // Custom Configurations
        item { CustomConfigShowcase() }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ============================================================================
// Live Recording Tab
// ============================================================================

@Composable
private fun LiveRecordingTab() {
    val recorderState = rememberRecorderState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "Live Recording Demo",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap the button and speak to see real-time waveform visualization",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Platform: ${System.getProperty("os.name")} | Permission: ${recorderState.permissionState}" +
                    if (recorderState.permissionState.toString() != "Granted") 
                        "\n⚠️ Check System Preferences → Privacy & Security → Microphone" 
                    else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (recorderState.permissionState.toString() == "Granted") 
                    Color.White.copy(alpha = 0.4f) 
                else 
                    Color(0xFFFF9800)
            )
        }

        // Permission handling
        if (recorderState.needsPermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Microphone Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            "Grant microphone access to see live waveform visualization",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = { recorderState.requestPermission() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        } else {
            // Record button
            item {
                RecordButton(
                    isRecording = recorderState.isRecording,
                    onClick = { recorderState.toggle() }
                )
            }

            // Status
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (recorderState.isRecording) "Recording... Speak now!" else "Tap to start recording",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (recorderState.isRecording) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f)
                    )
                    
                    // Debug info
                    if (recorderState.isRecording || recorderState.liveAmplitudes.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        val maxAmplitude = recorderState.liveAmplitudes.maxOrNull() ?: 0f
                        val nonZeroCount = recorderState.liveAmplitudes.count { it > 0.001f }
                        Text(
                            text = "Samples: ${recorderState.liveAmplitudes.size} | Max: ${"%.4f".format(maxAmplitude)} | Non-zero: $nonZeroCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (maxAmplitude > 0.01f) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        )
                        if (maxAmplitude < 0.001f && recorderState.liveAmplitudes.size > 10) {
                            Text(
                                text = "⚠️ Audio data is silent - check microphone permission in System Preferences",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                    
                    recorderState.error?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Error: ${error.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            }

            // Live waveform visualizations
            item {
                LiveWaveformShowcase(
                    amplitudes = recorderState.liveAmplitudes,
                    isRecording = recorderState.isRecording
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (isRecording) Color(0xFFE53935) else Color(0xFF4CAF50)
    val pulseAlpha = if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition()
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        alpha
    } else {
        0f
    }

    Box(contentAlignment = Alignment.Center) {
        // Pulse ring
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = pulseAlpha))
            )
        }
        
        // Main button
        Button(
            onClick = onClick,
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Text(
                text = if (isRecording) "Stop" else "Record",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun LiveWaveformShowcase(
    amplitudes: List<Float>,
    isRecording: Boolean
) {
    // Debug info
    Text(
        text = "Amplitudes: ${amplitudes.size} samples" + 
            if (amplitudes.isNotEmpty()) ", max: ${amplitudes.maxOrNull()?.let { "%.3f".format(it) }}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.5f)
    )
    
    val displayAmplitudes = if (amplitudes.isEmpty()) {
        // Show placeholder when no amplitudes
        List(50) { 0.1f }
    } else {
        amplitudes
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Mirrored style - best for recording
        ShowcaseCard(
            title = "Mirrored Style",
            subtitle = "Symmetric visualization - ideal for recording"
        ) {
            WaveformContainer(backgroundColor = Color(0xFF1A1A2E)) {
                AudioWaveform(
                    amplitudes = displayAmplitudes,
                    style = WaveformStyle.Mirrored(
                        barWidth = 4.dp,
                        barSpacing = 2.dp,
                        gap = 4.dp
                    ),
                    colors = if (isRecording) WaveformColors.GreenGradient else WaveformColors.solidColor(Color.Gray.copy(alpha = 0.3f)),
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        }

        // Bar style
        ShowcaseCard(
            title = "Bar Style",
            subtitle = "Classic vertical bars"
        ) {
            WaveformContainer(backgroundColor = Color(0xFF1A2E1A)) {
                AudioWaveform(
                    amplitudes = displayAmplitudes,
                    style = WaveformStyle.Bar(
                        width = 4.dp,
                        spacing = 2.dp,
                        alignment = WaveformAlignment.Center
                    ),
                    colors = if (isRecording) WaveformColors.BlueGradient else WaveformColors.solidColor(Color.Gray.copy(alpha = 0.3f)),
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        }

        // Spike style
        ShowcaseCard(
            title = "Spike Style",
            subtitle = "SoundCloud-inspired visualization"
        ) {
            WaveformContainer(backgroundColor = Color(0xFF2E1A2E)) {
                AudioWaveform(
                    amplitudes = displayAmplitudes,
                    style = WaveformStyle.Spike(
                        width = 2.dp,
                        spacing = 1.dp,
                        alignment = WaveformAlignment.Bottom
                    ),
                    colors = if (isRecording) WaveformColors.PurpleGradient else WaveformColors.solidColor(Color.Gray.copy(alpha = 0.3f)),
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
        }

        // Line style
        ShowcaseCard(
            title = "Line Style",
            subtitle = "Smooth connected waveform"
        ) {
            WaveformContainer(backgroundColor = Color(0xFF1A2E2E)) {
                AudioWaveform(
                    amplitudes = displayAmplitudes,
                    style = WaveformStyle.Line(
                        strokeWidth = 3.dp,
                        smoothing = 0.6f
                    ),
                    colors = if (isRecording) WaveformColors.OceanGradient else WaveformColors.solidColor(Color.Gray.copy(alpha = 0.3f)),
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
        }

        // Filled style
        ShowcaseCard(
            title = "Filled Style",
            subtitle = "Area chart visualization"
        ) {
            WaveformContainer(backgroundColor = Color(0xFF2E2E1A)) {
                AudioWaveform(
                    amplitudes = displayAmplitudes,
                    style = WaveformStyle.Filled(
                        smoothing = 0.5f,
                        alignment = WaveformAlignment.Bottom
                    ),
                    colors = if (isRecording) WaveformColors.SunsetGradient else WaveformColors.solidColor(Color.Gray.copy(alpha = 0.3f)),
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
        }
    }
}

@Composable
private fun ShowcaseCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            content()
        }
    }
}

@Composable
private fun WaveformContainer(
    label: String? = null,
    backgroundColor: Color = Color(0xFF2A2A2A),
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .padding(8.dp)
        ) {
            content()
        }
    }
}

// ============================================================================
// Demo Data
// ============================================================================

private fun generateSampleAmplitudes(count: Int = 50): List<Float> {
    return List(count) { i ->
        val base = sin(i * 0.3f) * 0.3f + 0.5f
        val noise = Random.nextFloat() * 0.2f
        (base + noise).coerceIn(0.1f, 1f)
    }
}

private fun generateSmoothWave(count: Int = 50, phase: Float = 0f): List<Float> {
    return List(count) { i ->
        val x = (i + phase) * 0.15f
        (sin(x) * 0.4f + 0.5f + sin(x * 2.3f) * 0.1f).coerceIn(0.1f, 1f)
    }
}

// ============================================================================
// Bar Style Showcase
// ============================================================================

@Composable
private fun BarStyleShowcase() {
    val amplitudes = remember { generateSampleAmplitudes() }

    ShowcaseCard(
        title = "Bar Style",
        subtitle = "Classic vertical bars with alignment options"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Default centered
            WaveformContainer("Center aligned (default)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(),
                    colors = WaveformColors.Green,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            // Top aligned
            WaveformContainer("Top aligned") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(alignment = WaveformAlignment.Top),
                    colors = WaveformColors.Blue,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            // Bottom aligned
            WaveformContainer("Bottom aligned") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(alignment = WaveformAlignment.Bottom),
                    colors = WaveformColors.Purple,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            // Wide bars with spacing
            WaveformContainer("Wide bars (6dp width, 4dp spacing)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(width = 6.dp, spacing = 4.dp, cornerRadius = 3.dp),
                    colors = WaveformColors.Orange,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}

// ============================================================================
// Spike Style Showcase
// ============================================================================

@Composable
private fun SpikeStyleShowcase() {
    val amplitudes = remember { generateSampleAmplitudes(80) }

    ShowcaseCard(
        title = "Spike Style",
        subtitle = "SoundCloud-inspired thin spikes"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Default spikes") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Spike(),
                    colors = WaveformColors.Cyan,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Dense spikes (1dp width, 0.5dp spacing)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Spike(width = 1.dp, spacing = 0.5.dp),
                    colors = WaveformColors.OceanGradient,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Bottom aligned spikes") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Spike(alignment = WaveformAlignment.Bottom),
                    colors = WaveformColors.NeonGradient,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}

// ============================================================================
// Line Style Showcase
// ============================================================================

@Composable
private fun LineStyleShowcase() {
    val amplitudes = remember { generateSmoothWave(60) }

    ShowcaseCard(
        title = "Line Style",
        subtitle = "Smooth connected waveform"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Smooth line (0.5 smoothing)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Line(smoothing = 0.5f),
                    colors = WaveformColors.BlueGradient,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Sharp line (no smoothing)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Line(smoothing = 0f, strokeWidth = 1.dp),
                    colors = WaveformColors.Green,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Thick smooth line") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Line(smoothing = 0.8f, strokeWidth = 4.dp),
                    colors = WaveformColors.SunsetGradient,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }
        }
    }
}

// ============================================================================
// Mirrored Style Showcase
// ============================================================================

@Composable
private fun MirroredStyleShowcase() {
    val amplitudes = remember { generateSampleAmplitudes() }

    ShowcaseCard(
        title = "Mirrored Style",
        subtitle = "Symmetric visualization ideal for recording"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Default mirrored") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Mirrored(),
                    colors = WaveformColors.PurpleGradient,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }

            WaveformContainer("Large gap (8dp)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Mirrored(gap = 8.dp, barWidth = 4.dp),
                    colors = WaveformColors.NeonGradient,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }

            WaveformContainer("Thin bars, no gap") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Mirrored(
                        barWidth = 2.dp,
                        barSpacing = 1.dp,
                        gap = 0.dp
                    ),
                    colors = WaveformColors.OceanGradient,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }
        }
    }
}

// ============================================================================
// Filled Style Showcase
// ============================================================================

@Composable
private fun FilledStyleShowcase() {
    val amplitudes = remember { generateSmoothWave(50) }

    ShowcaseCard(
        title = "Filled Style",
        subtitle = "Area chart visualization"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Bottom filled (default)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Filled(),
                    colors = WaveformColors.gradient(
                        listOf(Color(0xFF4CAF50).copy(alpha = 0.8f), Color(0xFF4CAF50).copy(alpha = 0.2f))
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Top filled") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Filled(alignment = WaveformAlignment.Top),
                    colors = WaveformColors.verticalGradient(
                        listOf(Color(0xFF2196F3).copy(alpha = 0.2f), Color(0xFF2196F3).copy(alpha = 0.8f))
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Center filled, high smoothing") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Filled(
                        smoothing = 0.7f,
                        alignment = WaveformAlignment.Center
                    ),
                    colors = WaveformColors.PurpleGradient,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }
        }
    }
}

// ============================================================================
// Playback Progress Showcase
// ============================================================================

@Composable
private fun PlaybackProgressShowcase() {
    val amplitudes = remember { generateSampleAmplitudes(60) }
    var progress by remember { mutableFloatStateOf(0.4f) }

    ShowcaseCard(
        title = "Playback Progress",
        subtitle = "Interactive seeking with progress indicator"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Tap or drag on the waveform to seek",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )

            WaveformContainer("Bar style with progress") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(),
                    colors = WaveformColors(
                        waveColor = androidx.compose.ui.graphics.SolidColor(Color(0xFF4CAF50)),
                        progressColor = androidx.compose.ui.graphics.SolidColor(Color.White),
                        inactiveColor = androidx.compose.ui.graphics.SolidColor(Color(0xFF4CAF50).copy(alpha = 0.3f))
                    ),
                    progress = progress,
                    onProgressChange = { progress = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Spike style with progress") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Spike(),
                    colors = WaveformColors(
                        waveColor = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF9800)),
                        progressColor = androidx.compose.ui.graphics.SolidColor(Color.White),
                        inactiveColor = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF9800).copy(alpha = 0.3f))
                    ),
                    progress = progress,
                    onProgressChange = { progress = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Progress:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

// ============================================================================
// Live Animation Showcase
// ============================================================================

@Composable
private fun LiveAnimationShowcase() {
    val amplitudes = remember { mutableStateListOf<Float>() }

    // Simulate live audio input
    LaunchedEffect(Unit) {
        while (true) {
            val newAmplitude = Random.nextFloat() * 0.6f + 0.2f
            amplitudes.add(newAmplitude)
            if (amplitudes.size > 50) {
                amplitudes.removeAt(0)
            }
            delay(100)
        }
    }

    ShowcaseCard(
        title = "Live Animation",
        subtitle = "Smooth amplitude transitions with animation"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Animated bars (simulated recording)") {
                AudioWaveform(
                    amplitudes = amplitudes.toList(),
                    style = WaveformStyle.Bar(),
                    colors = WaveformColors.Green,
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Animated mirrored (simulated recording)") {
                AudioWaveform(
                    amplitudes = amplitudes.toList(),
                    style = WaveformStyle.Mirrored(),
                    colors = WaveformColors.PurpleGradient,
                    animate = true,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }

            WaveformContainer("No animation (instant updates)") {
                AudioWaveform(
                    amplitudes = amplitudes.toList(),
                    style = WaveformStyle.Bar(),
                    colors = WaveformColors.Orange,
                    animate = false,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}

// ============================================================================
// Color Themes Showcase
// ============================================================================

@Composable
private fun ColorThemesShowcase() {
    val amplitudes = remember { generateSampleAmplitudes(40) }

    ShowcaseCard(
        title = "Color Themes",
        subtitle = "Pre-built color schemes and gradients"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Green", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Green,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blue", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Blue,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Purple", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Purple,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Orange", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Orange,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Red", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Red,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cyan", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    AudioWaveform(
                        amplitudes = amplitudes,
                        colors = WaveformColors.Cyan,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Gradients", style = MaterialTheme.typography.labelMedium, color = Color.White)

            WaveformContainer("Green Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.GreenGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }

            WaveformContainer("Blue Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.BlueGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }

            WaveformContainer("Purple Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.PurpleGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }

            WaveformContainer("Sunset Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.SunsetGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }

            WaveformContainer("Ocean Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.OceanGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }

            WaveformContainer("Neon Gradient") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    colors = WaveformColors.NeonGradient,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }
    }
}

// ============================================================================
// Custom Configuration Showcase
// ============================================================================

@Composable
private fun CustomConfigShowcase() {
    val amplitudes = remember { generateSampleAmplitudes(60) }

    ShowcaseCard(
        title = "Custom Configurations",
        subtitle = "Mix and match parameters for unique designs"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WaveformContainer("Spotify-style (dense spikes, bottom aligned)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Spike(
                        width = 2.dp,
                        spacing = 1.dp,
                        alignment = WaveformAlignment.Bottom
                    ),
                    colors = WaveformColors.solidColor(Color(0xFF1DB954)),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }

            WaveformContainer("Voice memo style (mirrored, minimal)") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Mirrored(
                        barWidth = 2.dp,
                        barSpacing = 2.dp,
                        gap = 2.dp
                    ),
                    colors = WaveformColors.solidColor(Color(0xFFFF3B30)),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }

            WaveformContainer("Podcast player style") {
                AudioWaveform(
                    amplitudes = amplitudes,
                    style = WaveformStyle.Bar(
                        width = 4.dp,
                        spacing = 2.dp,
                        cornerRadius = 2.dp,
                        alignment = WaveformAlignment.Center
                    ),
                    colors = WaveformColors.gradient(
                        listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            WaveformContainer("Equalizer style (top aligned, wide bars)") {
                AudioWaveform(
                    amplitudes = amplitudes.take(20),
                    style = WaveformStyle.Bar(
                        width = 12.dp,
                        spacing = 4.dp,
                        cornerRadius = 2.dp,
                        alignment = WaveformAlignment.Bottom
                    ),
                    colors = WaveformColors.verticalGradient(
                        listOf(Color(0xFF00FF88), Color(0xFFFFFF00), Color(0xFFFF4444))
                    ),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        }
    }
}
