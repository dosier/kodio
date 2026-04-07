package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.kodio.sample.icons.SampleIcons
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
import space.kodio.core.Kodio
import space.kodio.core.SampleEncoding
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.fromBytes

@Composable
fun PlaybackShowcase() {
    var recording by remember { mutableStateOf<AudioRecording?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isDragOver by remember { mutableStateOf(false) }
    var outputDevices by remember { mutableStateOf<List<AudioDevice.Output>>(emptyList()) }
    var selectedOutputDevice by remember { mutableStateOf<AudioDevice.Output?>(null) }

    LaunchedEffect(Unit) {
        outputDevices = Kodio.listOutputDevices()
    }

    val scope = rememberCoroutineScope()

    val loadAudioBytes: (String, ByteArray) -> Unit = { name, bytes ->
        isLoading = true
        error = null
        fileName = name
        scope.launch {
            try {
                val fileFormat = when {
                    name.endsWith(".aiff", true) || name.endsWith(".aif", true) -> AudioFileFormat.Aiff
                    name.endsWith(".au", true) || name.endsWith(".snd", true) -> AudioFileFormat.Au
                    else -> AudioFileFormat.Wav
                }
                val rec = AudioRecording.fromBytes(bytes, fileFormat)
                recording = rec
            } catch (e: AudioFileReadError.InvalidFile) {
                error = "Invalid audio file: ${e.message}"
            } catch (e: AudioFileReadError.UnsupportedFormat) {
                error = "Unsupported format: ${e.message}"
            } catch (e: Exception) {
                error = "Failed to load file: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    var isPicking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceSelector(
            label = "Output Device",
            devices = outputDevices,
            selected = selectedOutputDevice,
            onSelect = { selectedOutputDevice = it },
        )

        FileSelectionCard(
            fileName = fileName,
            isLoading = isLoading,
            isPicking = isPicking,
            isDragOver = isDragOver,
            onPickFile = {
                if (!isPicking) {
                    isPicking = true
                    scope.launch {
                        try {
                            val file = pickFile(listOf("wav", "wave", "aiff", "aif", "au", "snd"))
                            if (file != null) {
                                val bytes = file.readBytes()
                                loadAudioBytes(file.name, bytes)
                            }
                        } catch (e: Exception) {
                            error = "Failed to read file: ${e.message}"
                            isLoading = false
                        } finally {
                            isPicking = false
                        }
                    }
                }
            },
            onDragStateChange = { isDragOver = it },
            onFileDrop = { name, bytes -> loadAudioBytes(name, bytes) }
        )

        error?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        recording?.let { rec ->
            FileInfoCard(rec, fileName)
            PlaybackCard(rec, selectedOutputDevice)
        }
    }
}

@Composable
private fun FileSelectionCard(
    fileName: String?,
    isLoading: Boolean,
    isPicking: Boolean,
    isDragOver: Boolean,
    onPickFile: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onFileDrop: (name: String, bytes: ByteArray) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .audioFileDropTarget(onDragStateChange, onFileDrop)
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Text(
                        "Loading audio file...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (fileName != null) fileName else "Select an audio file",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        "WAV, AIFF, AU files supported \u2022 drag and drop on desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(onClick = onPickFile, enabled = !isPicking) {
                        Text(if (isPicking) "Picking..." else "Open File")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isDragOver,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DropOverlay()
        }
    }
}

@Composable
private fun DropOverlay() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val strokeWidth = 2.dp.toPx()
            val dash = 10.dp.toPx()
            val gap = 6.dp.toPx()
            val cornerPx = 12.dp.toPx()

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                )
            )
        }

        Text(
            "Drop audio file here",
            style = MaterialTheme.typography.titleMedium,
            color = primaryColor
        )
    }
}

@Composable
private fun FileInfoCard(recording: AudioRecording, fileName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            fileName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val format = recording.format
            val encodingDesc = when (val enc = format.encoding) {
                is SampleEncoding.PcmInt -> "${enc.bitDepth.bits}-bit PCM"
                is SampleEncoding.PcmFloat -> "${if (enc.precision == space.kodio.core.FloatPrecision.F32) "32" else "64"}-bit Float"
            }
            val channelDesc = when (format.channels) {
                Channels.Mono -> "Mono"
                Channels.Stereo -> "Stereo"
            }

            Text(
                text = "$encodingDesc, ${formatSampleRate(format.sampleRate)}, $channelDesc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Duration: ${formatDuration(recording.calculatedDuration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Size: ${formatFileSize(recording.sizeInBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaybackCard(recording: AudioRecording, device: AudioDevice.Output? = null) {
    val playerState = rememberPlayerState(recording, device = device)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AudioWaveform(
                amplitudes = generateStaticWaveform(recording),
                style = WaveformStyle.Bar(width = 3.dp, spacing = 1.dp),
                colors = WaveformColors.default(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerState.stop() },
                    enabled = playerState.isPlaying || playerState.isPaused
                ) {
                    Icon(SampleIcons.Stop, contentDescription = "Stop")
                }

                Spacer(Modifier.width(16.dp))

                FilledIconButton(
                    onClick = { playerState.toggle() },
                    enabled = !playerState.isLoading,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) SampleIcons.Pause else SampleIcons.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play"
                    )
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = when {
                        playerState.isLoading -> "Loading..."
                        playerState.isPlaying -> "Playing"
                        playerState.isPaused -> "Paused"
                        playerState.isFinished -> "Finished"
                        playerState.hasError -> "Error"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (playerState.hasError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            playerState.error?.let { err ->
                Text(
                    text = err.message ?: "Playback error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun generateStaticWaveform(recording: AudioRecording): List<Float> {
    val rawBytes = recording.toByteArray()
    if (rawBytes.isEmpty()) return emptyList()

    val barCount = 80
    val bytesPerSample = recording.format.bytesPerSample
    val totalSamples = rawBytes.size / bytesPerSample.coerceAtLeast(1)
    if (totalSamples == 0) return List(barCount) { 0f }

    val samplesPerBar = (totalSamples / barCount).coerceAtLeast(1)
    val amplitudes = mutableListOf<Float>()

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

private fun formatDuration(duration: kotlin.time.Duration): String {
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
