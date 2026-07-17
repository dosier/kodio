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
import space.kodio.compose.WaveformStyle
import space.kodio.compose.rememberPlayerState
import space.kodio.core.AudioDevice
import space.kodio.core.AudioRecording
import space.kodio.core.Channels
import space.kodio.core.Endianness
import space.kodio.core.FloatPrecision
import space.kodio.core.Kodio
import space.kodio.core.SampleEncoding
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.fromBytes
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.sample.icons.SampleIcons
import kotlin.math.abs
import kotlin.math.max
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
                        verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
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
                    "WAV · AIFF · AU · MP3  ·  drag and drop on desktop",
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
            shape = RoundedCornerShape(SampleDimens.PanelRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SampleDimens.PanelPadding),
                verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
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
    val canScrub = playerState.duration > Duration.ZERO && !playerState.isLoading

    Column(verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap)) {
        AudioWaveform(
            amplitudes = waveform,
            style = WaveformStyle.Bar(width = 3.dp, spacing = 1.dp),
            colors = sampleWaveformColors(),
            progress = playerState.progress,
            onProgressChange = if (canScrub) { p -> playerState.seekTo(p) } else null,
            animate = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
        )

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
            Text(
                text = err.message ?: "Playback error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
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

/**
 * Builds a static peak waveform for [recording].
 *
 * Must honor [SampleEncoding] (bit depth, signedness, endianness, float).
 * The previous LE-int16-only path looked fine for decoded MP3 (always PCM16 LE)
 * but collapsed 24/32-bit and float PCM (common in WAV/AIFF/AU) to near-silence.
 */
private fun generateStaticWaveform(recording: AudioRecording, barCount: Int = 96): List<Float> {
    val rawBytes = recording.toByteArray()
    if (rawBytes.isEmpty()) return emptyList()

    val format = recording.format
    val bytesPerSample = format.bytesPerSample.coerceAtLeast(1)
    val channelCount = format.channels.count.coerceAtLeast(1)
    val bytesPerFrame = format.bytesPerFrame.coerceAtLeast(bytesPerSample)
    val totalFrames = rawBytes.size / bytesPerFrame
    if (totalFrames == 0) return List(barCount) { 0f }

    val framesPerBar = (totalFrames / barCount).coerceAtLeast(1)
    val amplitudes = ArrayList<Float>(barCount)

    for (bar in 0 until barCount) {
        val startFrame = bar * framesPerBar
        val endFrame = (startFrame + framesPerBar).coerceAtMost(totalFrames)
        var peak = 0f

        for (frame in startFrame until endFrame) {
            val frameOffset = frame * bytesPerFrame
            for (ch in 0 until channelCount) {
                val sampleOffset = frameOffset + ch * bytesPerSample
                peak = max(peak, absSample(rawBytes, sampleOffset, format.encoding))
            }
        }

        amplitudes.add(peak.coerceIn(0.02f, 1f))
    }

    return amplitudes
}

private fun absSample(bytes: ByteArray, offset: Int, encoding: SampleEncoding): Float {
    return when (encoding) {
        is SampleEncoding.PcmInt -> {
            val bits = encoding.bitDepth.bits
            val bps = bits / 8
            if (offset < 0 || offset + bps > bytes.size) return 0f
            val raw = readPcmIntBits(bytes, offset, bps, encoding.endianness)
            val centered = if (encoding.signed) {
                signExtend(raw, bits)
            } else {
                raw - (1L shl (bits - 1))
            }
            val divisor = (1L shl (bits - 1)).toFloat()
            abs(centered.toFloat() / divisor).coerceIn(0f, 1f)
        }
        is SampleEncoding.PcmFloat -> when (encoding.precision) {
            FloatPrecision.F32 -> {
                if (offset + 4 > bytes.size) return 0f
                abs(Float.fromBits(readInt32Le(bytes, offset))).coerceIn(0f, 1f)
            }
            FloatPrecision.F64 -> {
                if (offset + 8 > bytes.size) return 0f
                abs(Double.fromBits(readInt64Le(bytes, offset)).toFloat()).coerceIn(0f, 1f)
            }
        }
    }
}

private fun readPcmIntBits(bytes: ByteArray, offset: Int, bps: Int, endianness: Endianness): Long {
    var value = 0L
    when (endianness) {
        Endianness.Little -> {
            for (i in 0 until bps) {
                value = value or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
            }
        }
        Endianness.Big -> {
            for (i in 0 until bps) {
                value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
        }
    }
    return value
}

private fun signExtend(value: Long, bits: Int): Long {
    val signBit = 1L shl (bits - 1)
    return if (value and signBit != 0L) {
        value or (-1L shl bits)
    } else {
        value
    }
}

private fun readInt32Le(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun readInt64Le(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
        value = value or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
    }
    return value
}

