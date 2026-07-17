package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import space.kodio.compose.AudioWaveform
import space.kodio.compose.WaveformColors
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberPlayerState
import space.kodio.core.AudioDevice
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.FloatPrecision
import space.kodio.core.Kodio
import space.kodio.core.SampleEncoding
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.fromBytes
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.sample.icons.SampleIcons
import kotlin.time.Duration

/** Supported picker extensions for decode + metadata. */
internal val AudioPickerExtensions = listOf("wav", "wave", "aiff", "aif", "au", "snd", "mp3")

private val KodAccent = Color(0xFF7C5CDB)
private val WideBreakpoint = 840.dp

private data class LoadedAudioFile(
    val fileName: String,
    val recording: AudioRecording,
    val metadata: AudioFileMetadata,
)

@Composable
fun PlaybackShowcase() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KodAccent,
            secondary = KodAccent,
            tertiary = KodAccent,
            primaryContainer = Color(0xFF2A2340),
            onPrimaryContainer = Color(0xFFE8E0FF),
            surface = Color(0xFF121218),
            surfaceVariant = Color(0xFF1C1C24),
            outline = Color(0xFF3A3A48),
            outlineVariant = Color(0xFF2A2A36),
        )
    ) {
        PlaybackShowcaseContent()
    }
}

@Composable
private fun PlaybackShowcaseContent() {
    var loaded by remember { mutableStateOf<LoadedAudioFile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isDragOver by remember { mutableStateOf(false) }
    var isPicking by remember { mutableStateOf(false) }
    var outputDevices by remember { mutableStateOf<List<AudioDevice.Output>>(emptyList()) }
    var selectedOutputDevice by remember { mutableStateOf<AudioDevice.Output?>(null) }

    LaunchedEffect(Unit) {
        outputDevices = Kodio.listOutputDevices()
    }

    val scope = rememberCoroutineScope()

    val loadAudioBytes: (String, ByteArray) -> Unit = { name, bytes ->
        isLoading = true
        error = null
        scope.launch {
            try {
                val metadata = AudioFileReader.readMetadata(bytes, name)
                val recording = AudioRecording.fromBytes(bytes, fileName = name)
                loaded = LoadedAudioFile(name, recording, metadata)
            } catch (e: AudioFileReadError.InvalidFile) {
                loaded = null
                error = "Invalid audio file — ${e.message ?: "unreadable container"}"
            } catch (e: AudioFileReadError.UnsupportedFormat) {
                loaded = null
                error = "Unsupported format — ${e.message ?: "codec not available"}"
            } catch (e: Exception) {
                loaded = null
                error = "Failed to load — ${e.message ?: e::class.simpleName}"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearLoaded() {
        loaded = null
        error = null
    }

    fun pickAndLoad() {
        if (isPicking) return
        isPicking = true
        scope.launch {
            try {
                val file = pickFile(AudioPickerExtensions)
                if (file != null) {
                    loadAudioBytes(file.name, file.readBytes())
                }
            } catch (e: Exception) {
                error = "Failed to read file — ${e.message ?: e::class.simpleName}"
                isLoading = false
            } finally {
                isPicking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaybackHeader()

        DeviceSelector(
            label = "Output device",
            devices = outputDevices,
            selected = selectedOutputDevice,
            onSelect = { selectedOutputDevice = it },
        )

        error?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val wide = maxWidth >= WideBreakpoint
            val current = loaded

            when {
                isLoading -> LoadingPane()
                current == null -> EmptyDropPane(
                    isPicking = isPicking,
                    isDragOver = isDragOver,
                    onPickFile = ::pickAndLoad,
                    onDragStateChange = { isDragOver = it },
                    onFileDrop = { name, bytes -> loadAudioBytes(name, bytes) },
                )
                wide -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        PreviewPane(
                            loaded = current,
                            device = selectedOutputDevice,
                            isPicking = isPicking,
                            isDragOver = isDragOver,
                            onPickFile = ::pickAndLoad,
                            onClear = ::clearLoaded,
                            onDragStateChange = { isDragOver = it },
                            onFileDrop = { name, bytes -> loadAudioBytes(name, bytes) },
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                        )
                        MetadataPane(
                            fileName = current.fileName,
                            metadata = current.metadata,
                            pcmBytes = current.recording.sizeInBytes,
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PreviewPane(
                            loaded = current,
                            device = selectedOutputDevice,
                            isPicking = isPicking,
                            isDragOver = isDragOver,
                            onPickFile = ::pickAndLoad,
                            onClear = ::clearLoaded,
                            onDragStateChange = { isDragOver = it },
                            onFileDrop = { name, bytes -> loadAudioBytes(name, bytes) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MetadataPane(
                            fileName = current.fileName,
                            metadata = current.metadata,
                            pcmBytes = current.recording.sizeInBytes,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Kodio",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "File playback",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Decode WAV / AIFF / AU / MP3 and inspect container metadata.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                "Reading file…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDropPane(
    isPicking: Boolean,
    isDragOver: Boolean,
    onPickFile: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onFileDrop: (name: String, bytes: ByteArray) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .audioFileDropTarget(onDragStateChange, onFileDrop),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    "Open an audio file",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "WAV · AIFF · AU · MP3  ·  drag and drop on desktop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onPickFile,
                    enabled = !isPicking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (isPicking) "Picking…" else "Choose file")
                }
            }
        }

        AnimatedVisibility(
            visible = isDragOver,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DropOverlay()
        }
    }
}

@Composable
private fun PreviewPane(
    loaded: LoadedAudioFile,
    device: AudioDevice.Output?,
    isPicking: Boolean,
    isDragOver: Boolean,
    onPickFile: () -> Unit,
    onClear: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onFileDrop: (name: String, bytes: ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.audioFileDropTarget(onDragStateChange, onFileDrop),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = loaded.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = onPickFile, enabled = !isPicking) {
                            Text(if (isPicking) "Picking…" else "Replace")
                        }
                        TextButton(
                            onClick = onClear,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Clear")
                        }
                    }
                }

                PlaybackControls(recording = loaded.recording, device = device)
            }
        }

        AnimatedVisibility(
            visible = isDragOver,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DropOverlay()
        }
    }
}

@Composable
private fun PlaybackControls(
    recording: AudioRecording,
    device: AudioDevice.Output?,
) {
    val playerState = rememberPlayerState(recording, device = device)
    val waveform = remember(recording) { generateStaticWaveform(recording) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AudioWaveform(
            amplitudes = waveform,
            style = WaveformStyle.Bar(width = 3.dp, spacing = 1.dp),
            colors = WaveformColors.solidColor(KodAccent),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )

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

            Spacer(Modifier.width(12.dp))

            FilledIconButton(
                onClick = { playerState.toggle() },
                enabled = !playerState.isLoading,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) SampleIcons.Pause else SampleIcons.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = when {
                    playerState.isLoading -> "Loading"
                    playerState.isPlaying -> "Playing"
                    playerState.isPaused -> "Paused"
                    playerState.isFinished -> "Finished"
                    playerState.hasError -> "Error"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (playerState.hasError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        playerState.error?.let { err ->
            Text(
                text = err.message ?: "Playback error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetadataPane(
    fileName: String,
    metadata: AudioFileMetadata,
    pcmBytes: Long,
    modifier: Modifier = Modifier,
) {
    var rawExpanded by remember(fileName) { mutableStateOf(false) }
    val tags = metadata.tags
    val hasIdentity = !tags.title.isNullOrBlank() ||
        !tags.artist.isNullOrBlank() ||
        !tags.album.isNullOrBlank()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Metadata",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "AudioFileReader.readMetadata()",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hasIdentity) {
                IdentityBlock(tags)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            TechnicalBlock(metadata = metadata, pcmBytes = pcmBytes)

            if (tags.raw.isNotEmpty() || hasSecondaryTags(tags)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { rawExpanded = !rawExpanded },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(if (rawExpanded) "Hide tags" else "Show tags")
                }
                AnimatedVisibility(visible = rawExpanded) {
                    TagsDetail(tags)
                }
            }
        }
    }
}

@Composable
private fun IdentityBlock(tags: AudioTags) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val credit = listOfNotNull(
            tags.artist?.takeIf { it.isNotBlank() },
            tags.album?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (credit.isNotEmpty()) {
            Text(
                text = credit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TechnicalBlock(
    metadata: AudioFileMetadata,
    pcmBytes: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Technical",
            style = MaterialTheme.typography.titleSmall,
        )
        MetaRow("container", containerLabel(metadata.container))
        MetaRow("encoding", encodingLabel(metadata.encoding))
        MetaRow("sample_rate", formatSampleRate(metadata.sampleRate))
        MetaRow(
            "channels",
            when (metadata.channels) {
                Channels.Mono -> "mono"
                Channels.Stereo -> "stereo"
            },
        )
        MetaRow("duration", formatDuration(metadata.duration))
        when (val enc = metadata.encoding) {
            is AudioEncodingInfo.Compressed.Mp3 -> {
                MetaRow(
                    "bitrate",
                    enc.bitrate?.let { "${it / 1000} kbps" } ?: "unknown",
                )
                MetaRow("vbr", if (enc.vbr) "true" else "false")
                MetaRow("mpeg", "MPEG-${enc.mpegVersion} L${enc.layer}")
            }
            is AudioEncodingInfo.Pcm -> {
                MetaRow("pcm_size", formatFileSize(pcmBytes))
            }
        }
    }
}

@Composable
private fun TagsDetail(tags: AudioTags) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.trackNumber?.let { MetaRow("track", it.toString()) }
        tags.year?.let { MetaRow("year", it.toString()) }
        tags.genre?.takeIf { it.isNotBlank() }?.let { MetaRow("genre", it) }
        tags.comment?.takeIf { it.isNotBlank() }?.let { MetaRow("comment", it) }
        tags.raw.toList().sortedBy { it.first }.forEach { (key, value) ->
            MetaRow(key, value)
        }
    }
}

@Composable
private fun MetaRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
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
private fun DropOverlay() {
    val accent = MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeWidth = 2.dp.toPx()
            val dash = 10.dp.toPx()
            val gap = 6.dp.toPx()
            val cornerPx = 10.dp.toPx()
            drawRoundRect(
                color = accent,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
                ),
            )
        }
        Text(
            "Drop audio file",
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
    }
}

private fun hasSecondaryTags(tags: AudioTags): Boolean =
    tags.trackNumber != null ||
        tags.year != null ||
        !tags.genre.isNullOrBlank() ||
        !tags.comment.isNullOrBlank()

internal fun containerLabel(format: AudioFileFormat): String = when (format) {
    is AudioFileFormat.Wav -> "WAV"
    is AudioFileFormat.Aiff -> "AIFF"
    is AudioFileFormat.Au -> "AU"
    is AudioFileFormat.Mp3 -> "MP3"
}

private fun encodingLabel(encoding: AudioEncodingInfo): String = when (encoding) {
    is AudioEncodingInfo.Pcm -> when (val pcm = encoding.encoding) {
        is SampleEncoding.PcmInt -> "${pcm.bitDepth.bits}-bit PCM"
        is SampleEncoding.PcmFloat ->
            "${if (pcm.precision == FloatPrecision.F32) "32" else "64"}-bit float"
    }
    is AudioEncodingInfo.Compressed.Mp3 -> "MP3"
}

private fun generateStaticWaveform(recording: AudioRecording): List<Float> {
    val rawBytes = recording.toByteArray()
    if (rawBytes.isEmpty()) return emptyList()

    val barCount = 96
    val bytesPerSample = recording.format.bytesPerSample
    val totalSamples = rawBytes.size / bytesPerSample.coerceAtLeast(1)
    if (totalSamples == 0) return List(barCount) { 0f }

    val samplesPerBar = (totalSamples / barCount).coerceAtLeast(1)
    val amplitudes = ArrayList<Float>(barCount)

    for (bar in 0 until barCount) {
        val startSample = bar * samplesPerBar
        var maxAmp = 0f

        for (s in startSample until (startSample + samplesPerBar).coerceAtMost(totalSamples)) {
            val byteOffset = s * bytesPerSample
            if (byteOffset + 1 >= rawBytes.size) break

            val sample = when (bytesPerSample) {
                1 -> (rawBytes[byteOffset].toInt() and 0xFF) - 128
                2 -> (rawBytes[byteOffset].toInt() and 0xFF) or
                    (rawBytes[byteOffset + 1].toInt() shl 8)
                else -> (rawBytes[byteOffset].toInt() and 0xFF) or
                    (rawBytes[byteOffset + 1].toInt() shl 8)
            }

            val normalized = kotlin.math.abs(sample.toFloat()) / when (bytesPerSample) {
                1 -> 128f
                2 -> 32768f
                else -> 32768f
            }
            if (normalized > maxAmp) maxAmp = normalized
        }

        amplitudes.add(maxAmp.coerceIn(0.02f, 1f))
    }

    return amplitudes
}

private fun formatSampleRate(rate: Int): String = when {
    rate >= 1000 -> "${formatDecimal(rate / 1000.0, 1)} kHz"
    else -> "$rate Hz"
}

private fun formatDuration(duration: Duration?): String {
    if (duration == null) return "—"
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
