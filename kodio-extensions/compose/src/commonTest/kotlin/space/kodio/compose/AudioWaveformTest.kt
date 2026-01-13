package space.kodio.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.*

/**
 * Test tags for AudioWaveform UI tests.
 */
object AudioWaveformTestTags {
    const val WAVEFORM = "waveform"
    const val WAVEFORM_STYLE = "waveform_style"
}

/**
 * Default test modifier with sizing for AudioWaveform tests.
 */
private fun Modifier.testWaveformDefaults(tag: String = AudioWaveformTestTags.WAVEFORM) = this
    .testTag(tag)
    .fillMaxWidth()
    .height(48.dp)

/**
 * UI tests for [AudioWaveform] composable.
 * These tests verify rendering, styling, and interaction behavior.
 */
@OptIn(ExperimentalTestApi::class)
class AudioWaveformTest {

    // ========================================================================
    // Basic Rendering Tests
    // ========================================================================

    @Test
    fun rendersWithEmptyAmplitudes() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = emptyList(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertIsDisplayed()
    }

    @Test
    fun rendersWithAmplitudes() = runComposeUiTest {
        val amplitudes = listOf(0.1f, 0.5f, 0.8f, 0.3f, 0.9f)
        
        setContent {
            AudioWaveform(
                amplitudes = amplitudes,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertIsDisplayed()
    }

    @Test
    fun handlesOutOfRangeAmplitudes() = runComposeUiTest {
        // Values outside 0-1 range should be clamped
        val amplitudes = listOf(-0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        
        setContent {
            AudioWaveform(
                amplitudes = amplitudes,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun rendersSingleAmplitude() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun rendersLargeAmplitudeList() = runComposeUiTest {
        val amplitudes = List(500) { index -> (index % 10) / 10f }
        
        setContent {
            AudioWaveform(
                amplitudes = amplitudes,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Style Tests
    // ========================================================================

    @Test
    fun barStyleRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Bar(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun barStyleWithCustomParameters() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Bar(
                    width = 6.dp,
                    spacing = 4.dp,
                    cornerRadius = 3.dp,
                    alignment = WaveformAlignment.Bottom
                ),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun spikeStyleRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Spike(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun spikeStyleWithCustomParameters() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Spike(
                    width = 1.dp,
                    spacing = 0.5.dp,
                    alignment = WaveformAlignment.Top
                ),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun lineStyleRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f),
                style = WaveformStyle.Line(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun lineStyleWithCustomParameters() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f),
                style = WaveformStyle.Line(
                    strokeWidth = 4.dp,
                    smoothing = 0.8f
                ),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun mirroredStyleRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Mirrored(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun mirroredStyleWithCustomParameters() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = WaveformStyle.Mirrored(
                    barWidth = 4.dp,
                    barSpacing = 3.dp,
                    gap = 8.dp
                ),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun filledStyleRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f),
                style = WaveformStyle.Filled(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun filledStyleWithCustomParameters() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f),
                style = WaveformStyle.Filled(
                    smoothing = 0.7f,
                    alignment = WaveformAlignment.Top
                ),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Alignment Tests
    // ========================================================================

    @Test
    fun topAlignmentRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                style = WaveformStyle.Bar(alignment = WaveformAlignment.Top),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun centerAlignmentRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                style = WaveformStyle.Bar(alignment = WaveformAlignment.Center),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun bottomAlignmentRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                style = WaveformStyle.Bar(alignment = WaveformAlignment.Bottom),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Color Tests
    // ========================================================================

    @Test
    fun solidColorRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                colors = WaveformColors.solidColor(Color.Red),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun gradientColorRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                colors = WaveformColors.gradient(listOf(Color.Blue, Color.Cyan)),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun presetColorsRender() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f),
                colors = WaveformColors.Green,
                modifier = Modifier.testWaveformDefaults("green")
            )
            AudioWaveform(
                amplitudes = listOf(0.5f),
                colors = WaveformColors.Blue,
                modifier = Modifier.testWaveformDefaults("blue")
            )
            AudioWaveform(
                amplitudes = listOf(0.5f),
                colors = WaveformColors.Purple,
                modifier = Modifier.testWaveformDefaults("purple")
            )
        }
        
        waitForIdle()
        
        onNodeWithTag("green").assertExists()
        onNodeWithTag("blue").assertExists()
        onNodeWithTag("purple").assertExists()
    }

    @Test
    fun presetGradientsRender() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f),
                colors = WaveformColors.GreenGradient,
                modifier = Modifier.testWaveformDefaults("greenGradient")
            )
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f),
                colors = WaveformColors.BlueGradient,
                modifier = Modifier.testWaveformDefaults("blueGradient")
            )
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f),
                colors = WaveformColors.PurpleGradient,
                modifier = Modifier.testWaveformDefaults("purpleGradient")
            )
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f),
                colors = WaveformColors.SunsetGradient,
                modifier = Modifier.testWaveformDefaults("sunsetGradient")
            )
        }
        
        waitForIdle()
        
        onNodeWithTag("greenGradient").assertExists()
        onNodeWithTag("blueGradient").assertExists()
        onNodeWithTag("purpleGradient").assertExists()
        onNodeWithTag("sunsetGradient").assertExists()
    }

    // ========================================================================
    // Progress Tests
    // ========================================================================

    @Test
    fun progressRendersCorrectly() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f, 0.4f),
                progress = 0.5f,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun progressAtZeroRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                progress = 0f,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun progressAtOneRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                progress = 1f,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Animation Tests
    // ========================================================================

    @Test
    fun animationEnabledRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                animate = true,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun animationDisabledRenders() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                animate = false,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Modifier Tests
    // ========================================================================

    @Test
    fun customHeightModifierIsApplied() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                modifier = Modifier
                    .testTag(AudioWaveformTestTags.WAVEFORM)
                    .fillMaxWidth()
                    .height(100.dp)
            )
        }
        
        waitForIdle()
        
        val node = onNodeWithTag(AudioWaveformTestTags.WAVEFORM)
        node.assertExists()
        node.assertIsDisplayed()
        
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height > 0f, "Height should be positive")
        assertTrue(bounds.width > 0f, "Width should be positive")
    }

    @Test
    fun customSizeModifierIsApplied() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f, 0.7f, 0.3f),
                modifier = Modifier
                    .testTag(AudioWaveformTestTags.WAVEFORM)
                    .size(200.dp, 64.dp)
            )
        }
        
        waitForIdle()
        
        val node = onNodeWithTag(AudioWaveformTestTags.WAVEFORM)
        node.assertExists()
        
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0f, "Width should be positive")
        assertTrue(bounds.height > 0f, "Height should be positive")
    }

    // ========================================================================
    // Recomposition Tests
    // ========================================================================

    @Test
    fun recomposesWithNewAmplitudes() = runComposeUiTest {
        var amplitudes by mutableStateOf(listOf(0.5f))
        
        setContent {
            AudioWaveform(
                amplitudes = amplitudes,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Update amplitudes
        amplitudes = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Update to empty
        amplitudes = emptyList()
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun recomposesWithNewProgress() = runComposeUiTest {
        var progress by mutableFloatStateOf(0f)
        
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                progress = progress,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Update progress
        progress = 0.5f
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Complete progress
        progress = 1f
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun recomposesWithNewStyle() = runComposeUiTest {
        var style: WaveformStyle by mutableStateOf(WaveformStyle.Bar())
        
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.3f, 0.6f, 0.9f),
                style = style,
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Change to spike
        style = WaveformStyle.Spike()
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
        
        // Change to mirrored
        style = WaveformStyle.Mirrored()
        
        waitForIdle()
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    // ========================================================================
    // Boundary Tests
    // ========================================================================

    @Test
    fun handlesBoundaryAmplitudes() = runComposeUiTest {
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.0f, 1.0f, 0.0f, 1.0f),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }

    @Test
    fun handlesMinimalAmplitudesForLineStyle() = runComposeUiTest {
        // Line style needs at least 2 points
        setContent {
            AudioWaveform(
                amplitudes = listOf(0.5f), // Single point
                style = WaveformStyle.Line(),
                modifier = Modifier.testWaveformDefaults()
            )
        }
        
        waitForIdle()
        
        // Should not crash, just won't render a line
        onNodeWithTag(AudioWaveformTestTags.WAVEFORM).assertExists()
    }
}
