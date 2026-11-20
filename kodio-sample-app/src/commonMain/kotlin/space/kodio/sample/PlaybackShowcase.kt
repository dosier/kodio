package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import space.kodio.compose.AudioWaveform
import space.kodio.compose.PlayerState
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberPlayerState
import space.kodio.compose.waveformPeaks
import space.kodio.core.AudioDevice
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.Kodio
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

private data class LoadedAudioFile(
    val fileName: String,
    val recording: AudioRecording,
    val metadata: AudioFileMetadata,
)

@Composable
fun PlaybackShowcase() {
    PlaybackShowcaseContent()
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

    // Hoisted above the wide/narrow layout branches so window resize does not
    // dispose PlayerState (PreviewPane is composed in separate when-branches).
    val playerState = rememberPlayerState(device = selectedOutputDevice)

    LaunchedEffect(Unit) {
        outputDevices = Kodio.listOutputDevices()
    }

    LaunchedEffect(loaded?.recording, playerState) {
        val recording = loaded?.recording
        if (recording != null) {
            playerState.loadAsync(recording)
        } else {
            playerState.resetAsync()
        }
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
            .padding(
                horizontal = SampleDimens.ScreenPaddingH,
                vertical = SampleDimens.ScreenPaddingV,
            ),
        verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
    ) {
        SampleScreenHeader(
            title = "File playback",
            subtitle = "Decode WAV / AIFF / AU / MP3 and inspect container metadata.",
        )

        DeviceSelector(
            label = "Output device",
            devices = outputDevices,
            selected = selectedOutputDevice,
            onSelect = { selectedOutputDevice = it },
        )

        error?.let { msg -> SampleErrorBanner(msg) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val wide = maxWidth >= SampleDimens.WideBreakpoint
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
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PreviewPane(
                            loaded = current,
                            playerState = playerState,
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
                        verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
                    ) {
                        PreviewPane(
                            loaded = current,
                            playerState = playerState,
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
            shape = RoundedCornerShape(SampleDimens.PanelRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Text(
                    "Open an audio file",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (platformKind == PlatformKind.Desktop) {
                        "WAV · AIFF · AU · MP3  ·  or drag and drop a file here"
                    } else {
                        "WAV · AIFF · AU · MP3"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    playerState: PlayerState,
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
            shape = RoundedCornerShape(SampleDimens.PanelRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SampleDimens.PanelPadding),
                verticalArrangement = Arrangement.spacedBy(SampleDimens.DenseGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SamplePanelLabel("Preview")
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

                PlaybackControls(
                    recording = loaded.recording,
                    playerState = playerState,
                )
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
    playerState: PlayerState,
) {
    val canScrub = playerState.duration > Duration.ZERO && !playerState.isLoading

    Column(verticalArrangement = Arrangement.spacedBy(SampleDimens.DenseGap)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barCount = ((maxWidth / 3.dp).toInt()).coerceIn(80, 320)
            val waveform = remember(recording, barCount) {
                recording.waveformPeaks(barCount)
            }
            AudioWaveform(
                amplitudes = waveform,
                style = WaveformStyle.Bar(width = 2.dp, spacing = 1.dp, minHeight = 3.dp),
                colors = sampleWaveformColors(),
                progress = playerState.progress,
                onProgressChange = if (canScrub) { p -> playerState.seekTo(p) } else null,
                animate = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { playerState.stop() },
                enabled = playerState.isPlaying || playerState.isPaused || playerState.progress > 0f,
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

        TimelineScrubber(
            progress = playerState.progress,
            position = playerState.position,
            duration = playerState.duration,
            enabled = canScrub,
            onSeek = { playerState.seekTo(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        playerState.error?.let { err ->
            SelectionContainer {
                Text(
                    text = err.message ?: "Playback error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TimelineScrubber(
    progress: Float,
    position: Duration,
    duration: Duration,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val clamped = progress.coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(duration) {
                            detectTapGestures { offset ->
                                val width = size.width.coerceAtLeast(1)
                                onSeek((offset.x / width).coerceIn(0f, 1f))
                            }
                        }.pointerInput(duration) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val width = size.width.coerceAtLeast(1)
                                onSeek((change.position.x / width).coerceIn(0f, 1f))
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeight = 4.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f
                val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val playheadX = size.width * clamped

                drawRoundRect(
                    color = track,
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = radius,
                )
                if (playheadX > 0f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, trackTop),
                        size = Size(playheadX, trackHeight),
                        cornerRadius = radius,
                    )
                }

                val cursorWidth = 2.dp.toPx()
                val cursorHeight = 16.dp.toPx()
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(
                        (playheadX - cursorWidth / 2f).coerceIn(0f, size.width - cursorWidth),
                        (size.height - cursorHeight) / 2f,
                    ),
                    size = Size(cursorWidth, cursorHeight),
                    cornerRadius = CornerRadius(cursorWidth / 2f, cursorWidth / 2f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackTime(position),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPlaybackTime(duration),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(SampleDimens.PanelRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SampleDimens.PanelPadding)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SamplePanelLabel("Metadata")
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
        SampleMetaRow("container", containerLabel(metadata.container))
        SampleMetaRow("encoding", encodingLabel(metadata.encoding))
        SampleMetaRow("sample_rate", formatSampleRate(metadata.sampleRate))
        SampleMetaRow(
            "channels",
            when (metadata.channels) {
                Channels.Mono -> "mono"
                Channels.Stereo -> "stereo"
            },
        )
        SampleMetaRow("duration", formatDurationShort(metadata.duration))
        when (val enc = metadata.encoding) {
            is AudioEncodingInfo.Compressed.Mp3 -> {
                SampleMetaRow(
                    "bitrate",
                    enc.bitrate?.let { "${it / 1000} kbps" } ?: "unknown",
                )
                SampleMetaRow("vbr", if (enc.vbr) "true" else "false")
                SampleMetaRow("mpeg", "MPEG-${enc.mpegVersion} L${enc.layer}")
            }
            is AudioEncodingInfo.Pcm -> {
                SampleMetaRow("pcm_size", formatFileSize(pcmBytes))
            }
        }
    }
}

@Composable
private fun TagsDetail(tags: AudioTags) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.trackNumber?.let { SampleMetaRow("track", it.toString()) }
        tags.year?.let { SampleMetaRow("year", it.toString()) }
        tags.genre?.takeIf { it.isNotBlank() }?.let { SampleMetaRow("genre", it) }
        tags.comment?.takeIf { it.isNotBlank() }?.let { SampleMetaRow("comment", it) }
        tags.raw.toList().sortedBy { it.first }.forEach { (key, value) ->
            SampleMetaRow(key, value)
        }
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

