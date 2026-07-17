package space.kodio.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.kodio.compose.AudioWaveform
import space.kodio.compose.RecorderState
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberPlayerState
import space.kodio.compose.rememberRecorderState
import space.kodio.core.AudioDevice
import space.kodio.core.AudioQuality
import space.kodio.core.AudioRecording
import space.kodio.core.Kodio
import space.kodio.sample.icons.SampleIcons

@Composable
fun RecordingShowcase() {
    var recordings by remember { mutableStateOf(listOf<AudioRecording>()) }
    var selectedRecordings by remember { mutableStateOf<Set<AudioRecording>>(emptySet()) }
    var selectedQuality by remember { mutableStateOf(AudioQuality.Default) }
    var inputDevices by remember { mutableStateOf<List<AudioDevice.Input>>(emptyList()) }
    var selectedInputDevice by remember { mutableStateOf<AudioDevice.Input?>(null) }
    var stitchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        inputDevices = Kodio.listInputDevices()
    }

    val recorderState = rememberRecorderState(
        quality = selectedQuality,
        device = selectedInputDevice,
        onRecordingComplete = { recording ->
            recordings = recordings + recording
        },
    )

    val scope = rememberCoroutineScope()
    val recorderActive = recorderState.isRecording || recorderState.isPaused

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = SampleDimens.ScreenPaddingH,
                vertical = SampleDimens.ScreenPaddingV,
            ),
    ) {
        val wide = maxWidth >= SampleDimens.WideBreakpoint

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
                ) {
                    SampleScreenHeader(
                        title = "Recording",
                        subtitle = "Capture from an input device into AudioRecording buffers.",
                    )
                    CaptureControls(
                        recorderState = recorderState,
                        inputDevices = inputDevices,
                        selectedInputDevice = selectedInputDevice,
                        onSelectDevice = { selectedInputDevice = it },
                        selectedQuality = selectedQuality,
                        onSelectQuality = { selectedQuality = it },
                        recorderActive = recorderActive,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                ) {
                    RecordingsPanel(
                        recordings = recordings,
                        selectedRecordings = selectedRecordings,
                        stitchError = stitchError,
                        onSelectedChange = { recording, selected ->
                            selectedRecordings = if (selected) {
                                selectedRecordings + recording
                            } else {
                                selectedRecordings - recording
                            }
                        },
                        onStitch = {
                            val source = recordings.filter { it in selectedRecordings }
                            if (source.size < 2) return@RecordingsPanel
                            scope.launch {
                                stitchError = null
                                try {
                                    val stitched = AudioRecording.concat(source)
                                    recordings = recordings + stitched
                                    selectedRecordings = emptySet()
                                } catch (e: Exception) {
                                    stitchError = e.message ?: e::class.simpleName
                                }
                            }
                        },
                        onSave = { recording ->
                            scope.launch { saveWavFile(recording.asAudioFlow()) }
                        },
                        onDelete = { recording ->
                            recordings = recordings - recording
                            selectedRecordings = selectedRecordings - recording
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
            ) {
                item {
                    SampleScreenHeader(
                        title = "Recording",
                        subtitle = "Capture from an input device into AudioRecording buffers.",
                    )
                }
                item {
                    CaptureControls(
                        recorderState = recorderState,
                        inputDevices = inputDevices,
                        selectedInputDevice = selectedInputDevice,
                        onSelectDevice = { selectedInputDevice = it },
                        selectedQuality = selectedQuality,
                        onSelectQuality = { selectedQuality = it },
                        recorderActive = recorderActive,
                    )
                }
                item {
                    RecordingsPanel(
                        recordings = recordings,
                        selectedRecordings = selectedRecordings,
                        stitchError = stitchError,
                        onSelectedChange = { recording, selected ->
                            selectedRecordings = if (selected) {
                                selectedRecordings + recording
                            } else {
                                selectedRecordings - recording
                            }
                        },
                        onStitch = {
                            val source = recordings.filter { it in selectedRecordings }
                            if (source.size < 2) return@RecordingsPanel
                            scope.launch {
                                stitchError = null
                                try {
                                    val stitched = AudioRecording.concat(source)
                                    recordings = recordings + stitched
                                    selectedRecordings = emptySet()
                                } catch (e: Exception) {
                                    stitchError = e.message ?: e::class.simpleName
                                }
                            }
                        },
                        onSave = { recording ->
                            scope.launch { saveWavFile(recording.asAudioFlow()) }
                        },
                        onDelete = { recording ->
                            recordings = recordings - recording
                            selectedRecordings = selectedRecordings - recording
                        },
                        modifier = Modifier.fillMaxWidth(),
                        embedded = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureControls(
    recorderState: RecorderState,
    inputDevices: List<AudioDevice.Input>,
    selectedInputDevice: AudioDevice.Input?,
    onSelectDevice: (AudioDevice.Input?) -> Unit,
    selectedQuality: AudioQuality,
    onSelectQuality: (AudioQuality) -> Unit,
    recorderActive: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap)) {
        DeviceSelector(
            label = "Input device",
            devices = inputDevices,
            selected = selectedInputDevice,
            onSelect = onSelectDevice,
            enabled = !recorderActive,
        )
        QualitySelector(
            selected = selectedQuality,
            onSelect = onSelectQuality,
            enabled = !recorderActive,
        )
        RecordingTransport(recorderState)
    }
}

@Composable
private fun RecordingTransport(recorderState: RecorderState) {
    SamplePanel {
        SamplePanelLabel("Capture")
        Text(
            text = when {
                recorderState.needsPermission -> "Microphone permission required"
                recorderState.isPaused -> "Paused"
                recorderState.isRecording -> "Recording"
                recorderState.error != null -> "Error: ${recorderState.error?.message}"
                else -> "Ready"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = when {
                recorderState.error != null -> MaterialTheme.colorScheme.error
                recorderState.isRecording -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (recorderState.needsPermission) {
            Button(onClick = { recorderState.requestPermission() }) {
                Text("Grant permission")
            }
        }

        if (recorderState.isRecording || recorderState.isPaused) {
            AudioWaveform(
                amplitudes = recorderState.liveAmplitudes,
                style = WaveformStyle.Mirrored(),
                colors = sampleWaveformColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                recorderState.isPaused -> Button(
                    onClick = { recorderState.resume() },
                    enabled = !recorderState.needsPermission,
                ) {
                    Icon(SampleIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Resume")
                }
                recorderState.isRecording -> Button(
                    onClick = { recorderState.pause() },
                    enabled = !recorderState.needsPermission,
                ) {
                    Icon(SampleIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pause")
                }
                else -> Button(
                    onClick = { recorderState.start() },
                    enabled = !recorderState.needsPermission,
                ) {
                    Icon(SampleIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Record")
                }
            }

            if (recorderState.isRecording || recorderState.isPaused) {
                OutlinedButton(
                    onClick = { recorderState.stop() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(SampleIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun RecordingsPanel(
    recordings: List<AudioRecording>,
    selectedRecordings: Set<AudioRecording>,
    stitchError: String?,
    onSelectedChange: (AudioRecording, Boolean) -> Unit,
    onStitch: () -> Unit,
    onSave: (AudioRecording) -> Unit,
    onDelete: (AudioRecording) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    SamplePanel(modifier = modifier, fillMaxHeight = !embedded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SamplePanelLabel("Session")
                Text(
                    "${recordings.size} recording${if (recordings.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = selectedRecordings.size >= 2,
                onClick = onStitch,
            ) {
                Text("Stitch (${selectedRecordings.size})")
            }
        }

        stitchError?.let { SampleErrorBanner(it) }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (recordings.isEmpty()) {
            Text(
                "Recordings appear here after you stop capture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else if (embedded) {
            recordings.forEach { recording ->
                RecordingItem(
                    recording = recording,
                    isSelected = recording in selectedRecordings,
                    onSelectedChange = { onSelectedChange(recording, it) },
                    onSave = { onSave(recording) },
                    onDelete = { onDelete(recording) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recordings) { recording ->
                    RecordingItem(
                        recording = recording,
                        isSelected = recording in selectedRecordings,
                        onSelectedChange = { onSelectedChange(recording, it) },
                        onSave = { onSave(recording) },
                        onDelete = { onDelete(recording) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : AudioDevice> DeviceSelector(
    label: String,
    devices: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    SamplePanel {
        SamplePanelLabel(label)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: "System default",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("System default") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            onSelect(device)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySelector(
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    enabled: Boolean,
) {
    SamplePanel {
        SamplePanelLabel("Quality")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AudioQuality.entries.forEachIndexed { index, quality ->
                SegmentedButton(
                    selected = quality == selected,
                    onClick = { onSelect(quality) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AudioQuality.entries.size,
                    ),
                ) {
                    Text(quality.name, maxLines = 1)
                }
            }
        }
        Text(
            text = selected.format.label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordingItem(
    recording: AudioRecording,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val playerState = rememberPlayerState(recording)

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange,
            )
            FilledIconButton(
                onClick = { playerState.toggle() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) SampleIcons.Pause else SampleIcons.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                SampleMetaRow("duration", formatDurationShort(recording.calculatedDuration), keyWidth = 72.dp)
                SampleMetaRow("format", recording.format.label, keyWidth = 72.dp)
                SampleMetaRow("size", formatFileSize(recording.sizeInBytes), keyWidth = 72.dp)
            }
            TextButton(onClick = onSave) { Text("Save") }
            IconButton(onClick = onDelete) {
                Icon(SampleIcons.Delete, contentDescription = "Delete")
            }
        }
    }
}
