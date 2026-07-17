package space.kodio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import space.kodio.sample.icons.SampleIcons

private enum class SampleDestination(val label: String) {
    Recording("Record"),
    Playback("Playback"),
    Conversion("Convert"),
    Transcription("Transcribe"),
}

private val SampleDestination.icon: ImageVector
    get() = when (this) {
        SampleDestination.Recording -> SampleIcons.Mic
        SampleDestination.Playback -> SampleIcons.PlayArrow
        SampleDestination.Conversion -> SampleIcons.Folder
        SampleDestination.Transcription -> SampleIcons.Schedule
    }

/**
 * Sample app demonstrating Kodio features.
 *
 * Showcases recording, file playback + metadata, format conversion,
 * and OpenAI Whisper transcription under a shared developer-tool theme.
 */
@Composable
@Preview
fun App() {
    SampleTheme {
        var selectedIndex by remember { mutableIntStateOf(1) }
        var apiKey by remember { mutableStateOf(getOpenAIApiKey()) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= SampleDimens.WideBreakpoint
            val destinations = SampleDestination.entries

            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            destinations.forEachIndexed { index, dest ->
                                NavigationBarItem(
                                    selected = selectedIndex == index,
                                    onClick = { selectedIndex = index },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = dest.label,
                                        )
                                    },
                                    label = { Text(dest.label) },
                                )
                            }
                        }
                    }
                },
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding(),
                ) {
                    if (wide) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Kodio",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            destinations.forEachIndexed { index, dest ->
                                NavigationRailItem(
                                    selected = selectedIndex == index,
                                    onClick = { selectedIndex = index },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = dest.label,
                                        )
                                    },
                                    label = { Text(dest.label) },
                                )
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (destinations[selectedIndex]) {
                            SampleDestination.Recording -> RecordingShowcase()
                            SampleDestination.Playback -> PlaybackShowcase()
                            SampleDestination.Conversion -> ConversionShowcase()
                            SampleDestination.Transcription -> {
                                if (apiKey.isBlank()) {
                                    ApiKeyInputScreen(onApiKeySubmit = { apiKey = it })
                                } else {
                                    TranscriptionShowcase(apiKey = apiKey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyInputScreen(
    onApiKeySubmit: (String) -> Unit,
) {
    var inputKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = SampleDimens.ScreenPaddingH,
                vertical = SampleDimens.ScreenPaddingV,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SamplePanel(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
        ) {
            SampleScreenHeader(
                title = "API key",
                subtitle = "OpenAI Whisper transcription needs a key from platform.openai.com.",
            )
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text("sk-…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Button(
                onClick = { onApiKeySubmit(inputKey.trim()) },
                enabled = inputKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
