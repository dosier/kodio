package space.kodio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.kodio.compose.rememberPlayerState
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.FloatPrecision
import space.kodio.core.IntBitDepth
import space.kodio.core.SampleEncoding
import space.kodio.core.SampleLayout
import space.kodio.core.io.convertAudio
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.fromBytes
import space.kodio.sample.icons.SampleIcons
import kotlin.math.abs

private val sampleRateOptions = listOf(8000, 16000, 22050, 44100, 48000)

private enum class EncodingChoice(val label: String) {
    Pcm8("8-bit PCM"),
    Pcm16("16-bit PCM"),
    Pcm24("24-bit PCM"),
    Pcm32("32-bit PCM"),
    PcmFloat32("32-bit Float"),
}

private fun encodingFromSource(enc: SampleEncoding): EncodingChoice = when (enc) {
    is SampleEncoding.PcmInt -> when (enc.bitDepth) {
        IntBitDepth.Eight -> EncodingChoice.Pcm8
        IntBitDepth.Sixteen -> EncodingChoice.Pcm16
        IntBitDepth.TwentyFour -> EncodingChoice.Pcm24
        IntBitDepth.ThirtyTwo -> EncodingChoice.Pcm32
    }
    is SampleEncoding.PcmFloat -> EncodingChoice.PcmFloat32
}

private fun snapSampleRate(rate: Int): Int =
    sampleRateOptions.minBy { abs(it - rate) }

private val encodableFileFormats: List<AudioFileFormat> =
    AudioFileFormat.entries.filter { it !is AudioFileFormat.Mp3 }

private fun encodingChoiceToSampleEncoding(
    choice: EncodingChoice,
    container: AudioFileFormat,
): SampleEncoding {
    val endianness = when (container) {
        is AudioFileFormat.Wav, is AudioFileFormat.Mp3 -> Endianness.Little
        is AudioFileFormat.Aiff, is AudioFileFormat.Au -> Endianness.Big
    }
    return when (choice) {
        EncodingChoice.Pcm8 ->
            SampleEncoding.PcmInt(
                IntBitDepth.Eight,
                endianness,
                SampleLayout.Interleaved,
                signed = container !is AudioFileFormat.Wav,
            )
        EncodingChoice.Pcm16 ->
            SampleEncoding.PcmInt(
                IntBitDepth.Sixteen,
                endianness,
                SampleLayout.Interleaved,
                signed = true,
            )
        EncodingChoice.Pcm24 ->
            SampleEncoding.PcmInt(
                IntBitDepth.TwentyFour,
                endianness,
                SampleLayout.Interleaved,
                signed = true,
            )
        EncodingChoice.Pcm32 ->
            SampleEncoding.PcmInt(
                IntBitDepth.ThirtyTwo,
                endianness,
                SampleLayout.Interleaved,
                signed = true,
            )
        EncodingChoice.PcmFloat32 ->
            SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionShowcase() {
    var sourceRecording by remember { mutableStateOf<AudioRecording?>(null) }
    var loadedContainer by remember { mutableStateOf<AudioFileFormat?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoadingFile by remember { mutableStateOf(false) }
    var isPicking by remember { mutableStateOf(false) }

    var targetFileFormat by remember { mutableStateOf<AudioFileFormat>(AudioFileFormat.Wav) }
    var targetSampleRate by remember { mutableIntStateOf(44100) }
    var targetChannels by remember { mutableStateOf(Channels.Stereo) }
    var targetEncodingChoice by remember { mutableStateOf(EncodingChoice.Pcm16) }

    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var sampleRateMenuExpanded by remember { mutableStateOf(false) }

    var statusError by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }

    var previewParamsKey by remember { mutableStateOf<String?>(null) }
    val playerState = rememberPlayerState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(sourceRecording) {
        val rec = sourceRecording ?: return@LaunchedEffect
        targetSampleRate = snapSampleRate(rec.format.sampleRate)
        targetChannels = rec.format.channels
        targetEncodingChoice = encodingFromSource(rec.format.encoding)
        targetFileFormat = when (val c = loadedContainer) {
            null, is AudioFileFormat.Mp3 -> AudioFileFormat.Wav
            else -> c
        }
        previewParamsKey = null
        playerState.reset()
    }

    val targetAudioFormat = remember(
        targetSampleRate,
        targetChannels,
        targetEncodingChoice,
        targetFileFormat,
    ) {
        AudioFormat(
            sampleRate = targetSampleRate,
            channels = targetChannels,
            encoding = encodingChoiceToSampleEncoding(targetEncodingChoice, targetFileFormat),
        )
    }

    val durationSec = sourceRecording?.calculatedDuration?.inWholeMilliseconds?.div(1000.0) ?: 0.0
    val estimatedPcmBytes = remember(targetAudioFormat, durationSec) {
        if (durationSec <= 0.0) 0L
        else {
            val bps = targetAudioFormat.sampleRate * targetAudioFormat.channels.count *
                targetAudioFormat.bytesPerSample
            (bps * durationSec).toLong()
        }
    }

    val aiffFloatBlocked =
        targetFileFormat == AudioFileFormat.Aiff && targetEncodingChoice == EncodingChoice.PcmFloat32

    val currentParamsKey = remember(
        targetFileFormat,
        targetSampleRate,
        targetChannels,
        targetEncodingChoice,
        sourceRecording,
    ) {
        "${sourceRecording?.sizeInBytes}-$targetFileFormat-$targetSampleRate-$targetChannels-$targetEncodingChoice"
    }

    fun buildConvertedFlow(): AudioFlow {
        val rec = sourceRecording!!
        val totalBytes = rec.sizeInBytes.toFloat()
        var processedBytes = 0L

        val frameSize = rec.format.bytesPerFrame.coerceAtLeast(1)
        val chunkSize = (64 * 1024 / frameSize) * frameSize

        val chunkedFlow = flow {
            val data = rec.toByteArray()
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + chunkSize, data.size)
                emit(data.copyOfRange(offset, end))
                offset = end
            }
        }

        val trackedSource = AudioFlow(
            rec.format,
            chunkedFlow.onEach { chunk ->
                processedBytes += chunk.size
                conversionProgress = if (totalBytes > 0f) {
                    (processedBytes / totalBytes).coerceIn(0f, 1f)
                } else 0f
            },
        )
        return trackedSource.convertAudio(targetAudioFormat)
    }

    fun pickAndLoad() {
        if (isPicking) return
        isPicking = true
        scope.launch {
            try {
                val file = pickFile(AudioPickerExtensions)
                if (file != null) {
                    isLoadingFile = true
                    loadError = null
                    statusError = null
                    fileName = file.name
                    val bytes = file.readBytes()
                    val metadata = AudioFileReader.readMetadata(bytes, file.name)
                    loadedContainer = metadata.container
                    sourceRecording = AudioRecording.fromBytes(bytes, fileName = file.name)
                }
            } catch (e: AudioFileReadError.InvalidFile) {
                loadError = "Invalid audio file: ${e.message}"
                sourceRecording = null
                loadedContainer = null
            } catch (e: AudioFileReadError.UnsupportedFormat) {
                loadError = "Unsupported format: ${e.message}"
                sourceRecording = null
                loadedContainer = null
            } catch (e: Exception) {
                loadError = "Failed to load: ${e.message}"
                sourceRecording = null
                loadedContainer = null
            } finally {
                isLoadingFile = false
                isPicking = false
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = SampleDimens.ScreenPaddingH,
                vertical = SampleDimens.ScreenPaddingV,
            ),
    ) {
        val wide = maxWidth >= SampleDimens.WideBreakpoint
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!wide) Modifier.verticalScroll(scroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
        ) {
            SampleScreenHeader(
                title = "Conversion",
                subtitle = "Resample / re-encode PCM and export WAV, AIFF, or AU.",
            )

            loadError?.let { SampleErrorBanner(it) }
            statusError?.let { SampleErrorBanner(it) }

            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
                    ) {
                        SourcePanel(
                            fileName = fileName,
                            isLoadingFile = isLoadingFile,
                            isPicking = isPicking,
                            sourceRecording = sourceRecording,
                            loadedContainer = loadedContainer,
                            onPick = ::pickAndLoad,
                        )
                        TargetFormatPanel(
                            targetFileFormat = targetFileFormat,
                            onFileFormat = { targetFileFormat = it },
                            targetSampleRate = targetSampleRate,
                            onSampleRate = {
                                targetSampleRate = it
                                previewParamsKey = null
                            },
                            sampleRateMenuExpanded = sampleRateMenuExpanded,
                            onSampleRateMenu = { sampleRateMenuExpanded = it },
                            targetEncodingChoice = targetEncodingChoice,
                            onEncoding = {
                                targetEncodingChoice = it
                                previewParamsKey = null
                            },
                            encodingMenuExpanded = encodingMenuExpanded,
                            onEncodingMenu = { encodingMenuExpanded = it },
                            targetChannels = targetChannels,
                            onChannels = {
                                targetChannels = it
                                previewParamsKey = null
                            },
                            aiffFloatBlocked = aiffFloatBlocked,
                        )
                    }
                    OutputPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        expand = true,
                        targetFileFormat = targetFileFormat,
                        targetSampleRate = targetSampleRate,
                        targetChannels = targetChannels,
                        targetEncodingChoice = targetEncodingChoice,
                        estimatedPcmBytes = estimatedPcmBytes,
                        isConverting = isConverting,
                        conversionProgress = conversionProgress,
                        playerState = playerState,
                        aiffFloatBlocked = aiffFloatBlocked,
                        hasSource = sourceRecording != null,
                        onPreview = {
                            scope.launch {
                                statusError = null
                                if (previewParamsKey == currentParamsKey &&
                                    (playerState.isReady || playerState.isPaused || playerState.isPlaying)
                                ) {
                                    playerState.toggle()
                                    return@launch
                                }
                                conversionProgress = 0f
                                isConverting = true
                                try {
                                    val converted = withContext(Dispatchers.Default) {
                                        AudioRecording.fromAudioFlow(buildConvertedFlow())
                                    }
                                    playerState.loadAsync(converted)
                                    playerState.playAsync()
                                    previewParamsKey = currentParamsKey
                                } catch (e: Exception) {
                                    statusError = "Conversion failed: ${e.message}"
                                    previewParamsKey = null
                                } finally {
                                    isConverting = false
                                }
                            }
                        },
                        onStop = { playerState.stop() },
                        onExport = {
                            scope.launch {
                                statusError = null
                                if (aiffFloatBlocked) return@launch
                                conversionProgress = 0f
                                isConverting = true
                                try {
                                    val flow = buildConvertedFlow()
                                    saveAudioWithFilePicker(
                                        audioFlow = AudioFlow(
                                            flow.format,
                                            flow.flowOn(Dispatchers.Default),
                                        ),
                                        fileFormat = targetFileFormat,
                                        suggestedName = "converted",
                                    )
                                } catch (e: Exception) {
                                    statusError = "Export failed: ${e.message}"
                                } finally {
                                    isConverting = false
                                }
                            }
                        },
                    )
                }
            } else {
                SourcePanel(
                    fileName = fileName,
                    isLoadingFile = isLoadingFile,
                    isPicking = isPicking,
                    sourceRecording = sourceRecording,
                    loadedContainer = loadedContainer,
                    onPick = ::pickAndLoad,
                )
                if (sourceRecording != null) {
                    TargetFormatPanel(
                        targetFileFormat = targetFileFormat,
                        onFileFormat = { targetFileFormat = it },
                        targetSampleRate = targetSampleRate,
                        onSampleRate = {
                            targetSampleRate = it
                            previewParamsKey = null
                        },
                        sampleRateMenuExpanded = sampleRateMenuExpanded,
                        onSampleRateMenu = { sampleRateMenuExpanded = it },
                        targetEncodingChoice = targetEncodingChoice,
                        onEncoding = {
                            targetEncodingChoice = it
                            previewParamsKey = null
                        },
                        encodingMenuExpanded = encodingMenuExpanded,
                        onEncodingMenu = { encodingMenuExpanded = it },
                        targetChannels = targetChannels,
                        onChannels = {
                            targetChannels = it
                            previewParamsKey = null
                        },
                        aiffFloatBlocked = aiffFloatBlocked,
                    )
                    OutputPanel(
                        modifier = Modifier.fillMaxWidth(),
                        expand = false,
                        targetFileFormat = targetFileFormat,
                        targetSampleRate = targetSampleRate,
                        targetChannels = targetChannels,
                        targetEncodingChoice = targetEncodingChoice,
                        estimatedPcmBytes = estimatedPcmBytes,
                        isConverting = isConverting,
                        conversionProgress = conversionProgress,
                        playerState = playerState,
                        aiffFloatBlocked = aiffFloatBlocked,
                        hasSource = true,
                        onPreview = {
                            scope.launch {
                                statusError = null
                                if (previewParamsKey == currentParamsKey &&
                                    (playerState.isReady || playerState.isPaused || playerState.isPlaying)
                                ) {
                                    playerState.toggle()
                                    return@launch
                                }
                                conversionProgress = 0f
                                isConverting = true
                                try {
                                    val converted = withContext(Dispatchers.Default) {
                                        AudioRecording.fromAudioFlow(buildConvertedFlow())
                                    }
                                    playerState.loadAsync(converted)
                                    playerState.playAsync()
                                    previewParamsKey = currentParamsKey
                                } catch (e: Exception) {
                                    statusError = "Conversion failed: ${e.message}"
                                    previewParamsKey = null
                                } finally {
                                    isConverting = false
                                }
                            }
                        },
                        onStop = { playerState.stop() },
                        onExport = {
                            scope.launch {
                                statusError = null
                                if (aiffFloatBlocked) return@launch
                                conversionProgress = 0f
                                isConverting = true
                                try {
                                    val flow = buildConvertedFlow()
                                    saveAudioWithFilePicker(
                                        audioFlow = AudioFlow(
                                            flow.format,
                                            flow.flowOn(Dispatchers.Default),
                                        ),
                                        fileFormat = targetFileFormat,
                                        suggestedName = "converted",
                                    )
                                } catch (e: Exception) {
                                    statusError = "Export failed: ${e.message}"
                                } finally {
                                    isConverting = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePanel(
    fileName: String?,
    isLoadingFile: Boolean,
    isPicking: Boolean,
    sourceRecording: AudioRecording?,
    loadedContainer: AudioFileFormat?,
    onPick: () -> Unit,
) {
    SamplePanel {
        SamplePanelLabel("Source")
        if (isLoadingFile) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                SampleStatusText("Reading file…")
            }
        } else {
            Text(
                fileName ?: "No file selected",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onPick, enabled = !isPicking) {
                Text(if (isPicking) "Picking…" else "Choose file")
            }
            sourceRecording?.let { rec ->
                loadedContainer?.let {
                    SampleMetaRow("container", containerLabel(it))
                }
                SampleMetaRow("sample_rate", formatSampleRate(rec.format.sampleRate))
                SampleMetaRow(
                    "channels",
                    if (rec.format.channels == Channels.Mono) "mono" else "stereo",
                )
                SampleMetaRow("encoding", encodingDescription(rec.format.encoding))
                SampleMetaRow("duration", formatDurationShort(rec.calculatedDuration))
                SampleMetaRow("pcm_size", formatFileSize(rec.sizeInBytes))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetFormatPanel(
    targetFileFormat: AudioFileFormat,
    onFileFormat: (AudioFileFormat) -> Unit,
    targetSampleRate: Int,
    onSampleRate: (Int) -> Unit,
    sampleRateMenuExpanded: Boolean,
    onSampleRateMenu: (Boolean) -> Unit,
    targetEncodingChoice: EncodingChoice,
    onEncoding: (EncodingChoice) -> Unit,
    encodingMenuExpanded: Boolean,
    onEncodingMenu: (Boolean) -> Unit,
    targetChannels: Channels,
    onChannels: (Channels) -> Unit,
    aiffFloatBlocked: Boolean,
) {
    SamplePanel {
        SamplePanelLabel("Target format")

        Text("File format", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            encodableFileFormats.forEachIndexed { index, fmt ->
                SegmentedButton(
                    selected = targetFileFormat == fmt,
                    onClick = { onFileFormat(fmt) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = encodableFileFormats.size,
                    ),
                ) {
                    Text(containerLabel(fmt), maxLines = 1)
                }
            }
        }

        Text("Sample rate", style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = sampleRateMenuExpanded,
            onExpandedChange = onSampleRateMenu,
        ) {
            OutlinedTextField(
                value = "$targetSampleRate Hz",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(sampleRateMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            ExposedDropdownMenu(
                expanded = sampleRateMenuExpanded,
                onDismissRequest = { onSampleRateMenu(false) },
            ) {
                sampleRateOptions.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text("$rate Hz") },
                        onClick = {
                            onSampleRate(rate)
                            onSampleRateMenu(false)
                        },
                    )
                }
            }
        }

        Text("Encoding", style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = encodingMenuExpanded,
            onExpandedChange = onEncodingMenu,
        ) {
            OutlinedTextField(
                value = targetEncodingChoice.label,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(encodingMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = encodingMenuExpanded,
                onDismissRequest = { onEncodingMenu(false) },
            ) {
                EncodingChoice.entries.forEach { enc ->
                    DropdownMenuItem(
                        text = { Text(enc.label) },
                        onClick = {
                            onEncoding(enc)
                            onEncodingMenu(false)
                        },
                    )
                }
            }
        }

        Text("Channels", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(Channels.Mono, Channels.Stereo).forEachIndexed { index, ch ->
                SegmentedButton(
                    selected = targetChannels == ch,
                    onClick = { onChannels(ch) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) {
                    Text(if (ch == Channels.Mono) "Mono" else "Stereo", maxLines = 1)
                }
            }
        }

        if (aiffFloatBlocked) {
            Text(
                "AIFF export does not support IEEE float. Choose another encoding or WAV/AU.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun OutputPanel(
    modifier: Modifier = Modifier,
    expand: Boolean = false,
    targetFileFormat: AudioFileFormat,
    targetSampleRate: Int,
    targetChannels: Channels,
    targetEncodingChoice: EncodingChoice,
    estimatedPcmBytes: Long,
    isConverting: Boolean,
    conversionProgress: Float,
    playerState: space.kodio.compose.PlayerState,
    aiffFloatBlocked: Boolean,
    hasSource: Boolean,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
) {
    SamplePanel(modifier = modifier, fillMaxHeight = expand) {
        SamplePanelLabel("Output")
        if (!hasSource) {
            SampleStatusText("Load a source file to preview and export.")
            return@SamplePanel
        }

        SampleMetaRow("container", containerLabel(targetFileFormat))
        SampleMetaRow("sample_rate", formatSampleRate(targetSampleRate))
        SampleMetaRow(
            "channels",
            if (targetChannels == Channels.Mono) "mono" else "stereo",
        )
        SampleMetaRow("encoding", targetEncodingChoice.label)
        SampleMetaRow("est_pcm", formatFileSize(estimatedPcmBytes))

        if (isConverting) {
            LinearProgressIndicator(
                progress = { conversionProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            SampleStatusText("Converting… ${(conversionProgress * 100).toInt()}%")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onStop,
                enabled = playerState.isPlaying || playerState.isPaused,
            ) {
                Icon(SampleIcons.Stop, contentDescription = "Stop")
            }
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = onPreview,
                enabled = !isConverting,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) SampleIcons.Pause else SampleIcons.PlayArrow,
                    contentDescription = "Preview",
                )
            }
            Spacer(Modifier.size(12.dp))
            SampleStatusText(
                text = when {
                    playerState.isLoading -> "Loading"
                    playerState.isPlaying -> "Playing"
                    playerState.isPaused -> "Paused"
                    playerState.isFinished -> "Finished"
                    playerState.hasError -> "Error"
                    else -> "Ready"
                },
                isError = playerState.hasError,
            )
        }

        playerState.error?.let { err ->
            Text(
                err.message ?: "Playback error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onExport,
            enabled = !isConverting && !aiffFloatBlocked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export…")
        }
    }
}
