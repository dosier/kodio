package space.kodio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

private fun audioFileFormatForName(name: String): AudioFileFormat = when {
    name.endsWith(".wav", true) || name.endsWith(".wave", true) -> AudioFileFormat.Wav
    name.endsWith(".aiff", true) || name.endsWith(".aif", true) -> AudioFileFormat.Aiff
    name.endsWith(".au", true) || name.endsWith(".snd", true) -> AudioFileFormat.Au
    else -> AudioFileFormat.Wav
}

private fun containerLabel(format: AudioFileFormat): String = when (format) {
    is AudioFileFormat.Wav -> "WAV"
    is AudioFileFormat.Aiff -> "AIFF"
    is AudioFileFormat.Au -> "AU"
}

private fun encodingChoiceToSampleEncoding(
    choice: EncodingChoice,
    container: AudioFileFormat,
): SampleEncoding {
    val endianness = when (container) {
        is AudioFileFormat.Wav -> Endianness.Little
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

private fun formatSampleRate(rate: Int): String = when {
    rate >= 1000 -> "${formatDecimal(rate / 1000.0, 1)} kHz"
    else -> "$rate Hz"
}

private fun formatDurationSeconds(duration: kotlin.time.Duration): String {
    val totalMs = duration.inWholeMilliseconds
    val seconds = totalMs / 1000
    val ms = totalMs % 1000
    return if (seconds > 0) "${seconds}.${(ms / 100)}s" else "${ms}ms"
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${formatDecimal(bytes / 1_048_576.0, 1)} MB"
    bytes >= 1024 -> "${formatDecimal(bytes / 1024.0, 1)} KB"
    else -> "$bytes B"
}

private fun encodingDescription(encoding: SampleEncoding): String = when (encoding) {
    is SampleEncoding.PcmInt -> "${encoding.bitDepth.bits}-bit PCM"
    is SampleEncoding.PcmFloat ->
        "${if (encoding.precision == FloatPrecision.F32) "32" else "64"}-bit Float"
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
        targetFileFormat = loadedContainer ?: AudioFileFormat.Wav
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
        conversionProgress = 0f
        val trackedSource = AudioFlow(
            rec.format,
            rec.asFlow(defensiveCopy = true).onEach { chunk ->
                processedBytes += chunk.size
                conversionProgress = if (totalBytes > 0f) {
                    (processedBytes / totalBytes).coerceIn(0f, 1f)
                } else 0f
            },
        )
        return trackedSource.convertAudio(targetAudioFormat)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Load", style = MaterialTheme.typography.titleSmall)
                    if (isLoadingFile) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            fileName ?: "No file selected",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Button(onClick = {
                            if (!isPicking) {
                                isPicking = true
                                scope.launch {
                                    try {
                                        val file = pickFile(
                                            listOf("wav", "wave", "aiff", "aif", "au", "snd"),
                                        )
                                        if (file != null) {
                                            isLoadingFile = true
                                            loadError = null
                                            statusError = null
                                            fileName = file.name
                                            val bytes = file.readBytes()
                                            val fmt = audioFileFormatForName(file.name)
                                            loadedContainer = fmt
                                            sourceRecording = AudioRecording.fromBytes(bytes, fmt)
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
                        }, enabled = !isPicking) {
                            Text(if (isPicking) "Picking…" else "Choose audio file")
                        }

                        sourceRecording?.let { rec ->
                            Spacer(Modifier.height(8.dp))
                            loadedContainer?.let {
                                Text(
                                    "Container: ${containerLabel(it)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${formatSampleRate(rec.format.sampleRate)}, " +
                                    "${if (rec.format.channels == Channels.Mono) "Mono" else "Stereo"}, " +
                                    encodingDescription(rec.format.encoding),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "Duration: ${formatDurationSeconds(rec.calculatedDuration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "PCM size: ${formatFileSize(rec.sizeInBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        sourceRecording?.let {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Target format", style = MaterialTheme.typography.titleSmall)

                        Text("File format", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AudioFileFormat.entries.forEachIndexed { index, fmt ->
                                SegmentedButton(
                                    selected = targetFileFormat == fmt,
                                    onClick = { targetFileFormat = fmt },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AudioFileFormat.entries.size,
                                    ),
                                ) {
                                    Text(containerLabel(fmt), maxLines = 1)
                                }
                            }
                        }

                        Text("Sample rate", style = MaterialTheme.typography.labelMedium)
                        ExposedDropdownMenuBox(
                            expanded = sampleRateMenuExpanded,
                            onExpandedChange = { sampleRateMenuExpanded = it },
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
                            )
                            ExposedDropdownMenu(
                                expanded = sampleRateMenuExpanded,
                                onDismissRequest = { sampleRateMenuExpanded = false },
                            ) {
                                sampleRateOptions.forEach { rate ->
                                    DropdownMenuItem(
                                        text = { Text("$rate Hz") },
                                        onClick = {
                                            targetSampleRate = rate
                                            previewParamsKey = null
                                            sampleRateMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Text("Encoding", style = MaterialTheme.typography.labelMedium)
                        ExposedDropdownMenuBox(
                            expanded = encodingMenuExpanded,
                            onExpandedChange = { encodingMenuExpanded = it },
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
                                onDismissRequest = { encodingMenuExpanded = false },
                            ) {
                                EncodingChoice.entries.forEach { enc ->
                                    DropdownMenuItem(
                                        text = { Text(enc.label) },
                                        onClick = {
                                            targetEncodingChoice = enc
                                            previewParamsKey = null
                                            encodingMenuExpanded = false
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
                                    onClick = {
                                        targetChannels = ch
                                        previewParamsKey = null
                                    },
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
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Output", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${containerLabel(targetFileFormat)} • ${formatSampleRate(targetSampleRate)}, " +
                                "${if (targetChannels == Channels.Mono) "Mono" else "Stereo"}, " +
                                targetEncodingChoice.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Estimated PCM size: ${formatFileSize(estimatedPcmBytes)} " +
                                "(plus container overhead)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (isConverting) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { conversionProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "Converting… ${(conversionProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { playerState.stop() },
                                enabled = playerState.isPlaying || playerState.isPaused,
                            ) {
                                Icon(SampleIcons.Stop, contentDescription = "Stop")
                            }
                            Spacer(Modifier.size(8.dp))
                            FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        statusError = null
                                        if (previewParamsKey == currentParamsKey &&
                                            (playerState.isReady || playerState.isPaused || playerState.isPlaying)
                                        ) {
                                            playerState.toggle()
                                            return@launch
                                        }
                                        isConverting = true
                                        try {
                                            val converted = AudioRecording.fromAudioFlow(buildConvertedFlow())
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
                                enabled = !isConverting,
                                modifier = Modifier.size(56.dp),
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) SampleIcons.Pause
                                    else SampleIcons.PlayArrow,
                                    contentDescription = "Preview",
                                )
                            }
                            Spacer(Modifier.size(16.dp))
                            Text(
                                when {
                                    playerState.isLoading -> "Loading…"
                                    playerState.isPlaying -> "Playing"
                                    playerState.isPaused -> "Paused"
                                    playerState.isFinished -> "Finished"
                                    playerState.hasError -> "Error"
                                    else -> "Ready"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (playerState.hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            onClick = {
                                scope.launch {
                                    statusError = null
                                    if (aiffFloatBlocked) return@launch
                                    isConverting = true
                                    try {
                                        val flow = buildConvertedFlow()
                                        saveAudioWithFilePicker(
                                            audioFlow = flow,
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
                            enabled = !isConverting && !aiffFloatBlocked,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Export…")
                        }
                    }
                }
            }
        }

        loadError?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        statusError?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
