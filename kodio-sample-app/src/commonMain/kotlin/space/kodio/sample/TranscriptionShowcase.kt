package space.kodio.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import space.kodio.core.AudioFlow
import space.kodio.core.AudioRecording
import space.kodio.core.Kodio
import space.kodio.core.Recorder
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.fromBytes
import space.kodio.core.logging.kodioLogger
import space.kodio.core.security.AudioPermissionManager
import space.kodio.sample.icons.SampleIcons
import space.kodio.transcription.TranscriptionResult
import space.kodio.transcription.cloud.OpenAIWhisperEngine
import space.kodio.transcription.transcribe
import kotlin.time.Duration

private val logger = kodioLogger("TranscriptionShowcase")
private fun log(message: String) = logger.info { message }

// Matches OpenAI Whisper list pricing ($0.006 / minute) used by OpenAIWhisperEngine.
private const val WHISPER_PRICE_PER_MINUTE_USD = 0.006

/**
 * Decodes a picked WAV/AIFF/AU/MP3 file (auto-detect via [AudioRecording.fromBytes])
 * and transcribes it via the same chunked [OpenAIWhisperEngine] used by the live
 * recording tab. Each chunk is uploaded separately so results stream in as the
 * file is processed.
 */
suspend fun transcribeFile(
    file: PlatformFile,
    engine: OpenAIWhisperEngine,
): Flow<TranscriptionResult> {
    val bytes = file.readBytes()
    val recording = AudioRecording.fromBytes(bytes, fileName = file.name)
    return recording.asAudioFlow().transcribe(engine)
}

/**
 * Demonstrates transcription using Kodio's transcription extension.
 * Supports both live recording and file upload.
 */
@Composable
fun TranscriptionShowcase(
    apiKey: String,
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = SampleDimens.ScreenPaddingH,
                vertical = SampleDimens.ScreenPaddingV,
            ),
        verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
    ) {
        SampleScreenHeader(
            title = "Transcription",
            subtitle = "Stream audio to OpenAI Whisper via chunked AudioFlow.",
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedMode == 0,
                onClick = { selectedMode = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Live")
            }
            SegmentedButton(
                selected = selectedMode == 1,
                onClick = { selectedMode = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("File")
            }
        }

        when (selectedMode) {
            0 -> LiveRecordingTab(apiKey = apiKey, modifier = Modifier.weight(1f))
            1 -> FileUploadTab(apiKey = apiKey, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveRecordingTab(
    apiKey: String,
    modifier: Modifier = Modifier,
) {
    var isTranscribing by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var finalSegments by remember { mutableStateOf(listOf<TranscriptionSegment>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionState by remember { mutableStateOf(AudioPermissionManager.State.Unknown) }
    var transcriptCache by remember { mutableStateOf<TranscriptCache?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val engine = remember { OpenAIWhisperEngine(apiKey = apiKey, chunkDurationSeconds = 3) }

    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var transcriptionJob by remember { mutableStateOf<Job?>(null) }
    var audioForwardJob by remember { mutableStateOf<Job?>(null) }
    var audioChannel by remember { mutableStateOf<Channel<ByteArray>?>(null) }

    LaunchedEffect(Unit) {
        permissionState = Kodio.microphonePermission.refresh()
    }

    DisposableEffect(Unit) {
        onDispose {
            audioForwardJob?.cancel()
            audioChannel?.close()
            transcriptionJob?.cancel()
            recorder?.release()
            engine.release()
        }
    }

    LaunchedEffect(finalSegments.size) {
        if (finalSegments.isNotEmpty()) {
            listState.animateScrollToItem(finalSegments.size - 1)
        }
    }

    val needsPermission = permissionState != AudioPermissionManager.State.Granted

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= SampleDimens.WideBreakpoint

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
                ) {
                    LiveControlsPanel(
                        needsPermission = needsPermission,
                        isTranscribing = isTranscribing,
                        isFinishing = isFinishing,
                        apiKeyBlank = apiKey.isBlank(),
                        error = error,
                        onGrantPermission = {
                            scope.launch {
                                Kodio.microphonePermission.request()
                                permissionState = Kodio.microphonePermission.refresh()
                            }
                        },
                        onToggle = {
                            if (isTranscribing) {
                                log("=== Stopping Recording (will finish transcribing buffered audio) ===")
                                isFinishing = true
                                recorder?.stop()
                                audioForwardJob?.cancel()
                                audioChannel?.close()
                                log("Audio channel closed - transcription will process remaining buffer")
                            } else {
                                error = null
                                transcriptCache = createTranscriptCache(sessionLabel = null)
                                isTranscribing = true

                                transcriptionJob = scope.launch {
                                    try {
                                        log("=== Starting Transcription ===")
                                        log("API Key present: ${apiKey.isNotBlank()}, prefix: ${apiKey.take(10)}...")

                                        val newRecorder = Kodio.recorder()
                                        recorder = newRecorder
                                        newRecorder.start()

                                        val liveFlow = newRecorder.liveAudioFlow
                                        val format = newRecorder.format

                                        if (liveFlow != null) {
                                            val channel = Channel<ByteArray>(Channel.UNLIMITED)
                                            audioChannel = channel

                                            audioForwardJob = scope.launch {
                                                try {
                                                    liveFlow.collect { chunk ->
                                                        channel.send(chunk)
                                                    }
                                                } catch (e: Exception) {
                                                    log("Audio forwarding ended: ${e.message}")
                                                }
                                            }

                                            val audioFlow = AudioFlow(format, channel.consumeAsFlow())
                                            audioFlow.transcribe(engine)
                                                .onStart { log("Transcription flow started") }
                                                .onCompletion { cause ->
                                                    if (cause == null) {
                                                        log("Transcription flow completed successfully!")
                                                    } else if (cause is kotlinx.coroutines.CancellationException) {
                                                        log("Transcription flow cancelled")
                                                    } else {
                                                        log("Transcription flow completed with error: $cause")
                                                    }
                                                    audioForwardJob?.cancel()
                                                    audioForwardJob = null
                                                    audioChannel?.close()
                                                    audioChannel = null
                                                    recorder?.release()
                                                    recorder = null
                                                    transcriptionJob = null
                                                    isTranscribing = false
                                                    isFinishing = false
                                                }
                                                .catch { e ->
                                                    if (e !is kotlinx.coroutines.CancellationException) {
                                                        log("ERROR: Transcription error in catch: ${e.message}")
                                                        logger.error(e) { "Transcription error in catch" }
                                                        error = "Transcription error: ${e.message}"
                                                    }
                                                }
                                                .collect { result ->
                                                    when (result) {
                                                        is TranscriptionResult.Partial -> {
                                                            partialText = result.text
                                                        }
                                                        is TranscriptionResult.Final -> {
                                                            if (result.text.isNotBlank()) {
                                                                transcriptCache?.appendFinal(
                                                                    start = result.startTime ?: Duration.ZERO,
                                                                    end = result.endTime ?: Duration.ZERO,
                                                                    text = result.text,
                                                                )
                                                                finalSegments = finalSegments + TranscriptionSegment(
                                                                    text = result.text,
                                                                    confidence = result.confidence,
                                                                )
                                                            }
                                                            partialText = ""
                                                        }
                                                        is TranscriptionResult.Error -> {
                                                            error = result.message
                                                            if (!result.isRecoverable) {
                                                                isTranscribing = false
                                                                newRecorder.stop()
                                                                newRecorder.release()
                                                            }
                                                        }
                                                    }
                                                }
                                        } else {
                                            error = "Failed to get audio stream"
                                            isTranscribing = false
                                        }
                                    } catch (e: Exception) {
                                        if (e !is kotlinx.coroutines.CancellationException) {
                                            log("ERROR: Exception in transcription: ${e.message}")
                                            logger.error(e) { "Exception in transcription" }
                                            error = "Error: ${e.message}"
                                        }
                                        audioForwardJob?.cancel()
                                        audioForwardJob = null
                                        audioChannel?.close()
                                        audioChannel = null
                                        recorder?.release()
                                        recorder = null
                                        transcriptionJob = null
                                        isTranscribing = false
                                        isFinishing = false
                                    }
                                }
                            }
                        },
                    )
                }
                TranscriptPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    finalSegments = finalSegments,
                    partialText = partialText,
                    isActive = isTranscribing,
                    emptyHint = "Start live transcription to stream segments here.",
                    listState = listState,
                    transcriptCache = transcriptCache,
                    onClear = {
                        finalSegments = emptyList()
                        partialText = ""
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
            ) {
                LiveControlsPanel(
                    needsPermission = needsPermission,
                    isTranscribing = isTranscribing,
                    isFinishing = isFinishing,
                    apiKeyBlank = apiKey.isBlank(),
                    error = error,
                    onGrantPermission = {
                        scope.launch {
                            Kodio.microphonePermission.request()
                            permissionState = Kodio.microphonePermission.refresh()
                        }
                    },
                    onToggle = {
                        if (isTranscribing) {
                            isFinishing = true
                            recorder?.stop()
                            audioForwardJob?.cancel()
                            audioChannel?.close()
                        } else {
                            error = null
                            transcriptCache = createTranscriptCache(sessionLabel = null)
                            isTranscribing = true
                            transcriptionJob = scope.launch {
                                try {
                                    val newRecorder = Kodio.recorder()
                                    recorder = newRecorder
                                    newRecorder.start()
                                    val liveFlow = newRecorder.liveAudioFlow
                                    val format = newRecorder.format
                                    if (liveFlow != null) {
                                        val channel = Channel<ByteArray>(Channel.UNLIMITED)
                                        audioChannel = channel
                                        audioForwardJob = scope.launch {
                                            try {
                                                liveFlow.collect { chunk -> channel.send(chunk) }
                                            } catch (_: Exception) {
                                            }
                                        }
                                        AudioFlow(format, channel.consumeAsFlow())
                                            .transcribe(engine)
                                            .onCompletion {
                                                audioForwardJob?.cancel()
                                                audioForwardJob = null
                                                audioChannel?.close()
                                                audioChannel = null
                                                recorder?.release()
                                                recorder = null
                                                transcriptionJob = null
                                                isTranscribing = false
                                                isFinishing = false
                                            }
                                            .catch { e ->
                                                if (e !is kotlinx.coroutines.CancellationException) {
                                                    error = "Transcription error: ${e.message}"
                                                }
                                            }
                                            .collect { result ->
                                                when (result) {
                                                    is TranscriptionResult.Partial -> partialText = result.text
                                                    is TranscriptionResult.Final -> {
                                                        if (result.text.isNotBlank()) {
                                                            transcriptCache?.appendFinal(
                                                                start = result.startTime ?: Duration.ZERO,
                                                                end = result.endTime ?: Duration.ZERO,
                                                                text = result.text,
                                                            )
                                                            finalSegments = finalSegments + TranscriptionSegment(
                                                                text = result.text,
                                                                confidence = result.confidence,
                                                            )
                                                        }
                                                        partialText = ""
                                                    }
                                                    is TranscriptionResult.Error -> {
                                                        error = result.message
                                                        if (!result.isRecoverable) {
                                                            isTranscribing = false
                                                            newRecorder.stop()
                                                            newRecorder.release()
                                                        }
                                                    }
                                                }
                                            }
                                    } else {
                                        error = "Failed to get audio stream"
                                        isTranscribing = false
                                    }
                                } catch (e: Exception) {
                                    if (e !is kotlinx.coroutines.CancellationException) {
                                        error = "Error: ${e.message}"
                                    }
                                    audioForwardJob?.cancel()
                                    audioForwardJob = null
                                    audioChannel?.close()
                                    audioChannel = null
                                    recorder?.release()
                                    recorder = null
                                    transcriptionJob = null
                                    isTranscribing = false
                                    isFinishing = false
                                }
                            }
                        }
                    },
                )
                TranscriptPanel(
                    modifier = Modifier.weight(1f),
                    finalSegments = finalSegments,
                    partialText = partialText,
                    isActive = isTranscribing,
                    emptyHint = "Start live transcription to stream segments here.",
                    listState = listState,
                    transcriptCache = transcriptCache,
                    onClear = {
                        finalSegments = emptyList()
                        partialText = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveControlsPanel(
    needsPermission: Boolean,
    isTranscribing: Boolean,
    isFinishing: Boolean,
    apiKeyBlank: Boolean,
    error: String?,
    onGrantPermission: () -> Unit,
    onToggle: () -> Unit,
) {
    SamplePanel {
        SamplePanelLabel("Live capture")
        SampleStatusText(
            text = when {
                needsPermission -> "permission_required"
                isFinishing -> "finishing"
                isTranscribing -> "listening"
                else -> "idle"
            },
            isError = needsPermission,
        )

        if (needsPermission) {
            Button(onClick = onGrantPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Grant permission")
            }
        }

        AnimatedVisibility(visible = isTranscribing, enter = fadeIn(), exit = fadeOut()) {
            SampleStatusText(
                if (isFinishing) "Draining buffered chunks…" else "Streaming mic → Whisper",
            )
        }

        error?.let { SampleErrorBanner(it) }

        Button(
            onClick = onToggle,
            enabled = !needsPermission && !apiKeyBlank && !isFinishing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    isFinishing -> MaterialTheme.colorScheme.primaryContainer
                    isTranscribing -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            ),
        ) {
            Text(
                when {
                    isFinishing -> "Finishing…"
                    isTranscribing -> "Stop"
                    else -> "Start"
                },
            )
        }
    }
}

@Composable
private fun FileUploadTab(
    apiKey: String,
    modifier: Modifier = Modifier,
) {
    var isTranscribing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var finalSegments by remember { mutableStateOf(listOf<TranscriptionSegment>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var totalDurationSeconds by remember { mutableDoubleStateOf(0.0) }
    var totalCost by remember { mutableDoubleStateOf(0.0) }

    var isPicking by remember { mutableStateOf(false) }
    var transcriptCache by remember { mutableStateOf<TranscriptCache?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val engine = remember(apiKey) { OpenAIWhisperEngine(apiKey = apiKey, chunkDurationSeconds = 10) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    LaunchedEffect(finalSegments.size) {
        if (finalSegments.isNotEmpty()) {
            listState.animateScrollToItem(finalSegments.size - 1)
        }
    }

    fun pickAndTranscribe() {
        if (isPicking) return
        isPicking = true
        scope.launch {
            try {
                val file = pickFile(AudioPickerExtensions)
                if (file != null) {
                    selectedFileName = file.name
                    error = null
                    partialText = ""
                    finalSegments = emptyList()
                    totalDurationSeconds = 0.0
                    totalCost = 0.0
                    transcriptCache = createTranscriptCache(sessionLabel = file.name)
                    isTranscribing = true

                    try {
                        transcribeFile(file, engine).collect { result ->
                            when (result) {
                                is TranscriptionResult.Partial -> partialText = result.text
                                is TranscriptionResult.Final -> {
                                    if (result.text.isNotBlank()) {
                                        transcriptCache?.appendFinal(
                                            start = result.startTime ?: Duration.ZERO,
                                            end = result.endTime ?: Duration.ZERO,
                                            text = result.text,
                                        )
                                        finalSegments = finalSegments + TranscriptionSegment(
                                            text = result.text,
                                            confidence = result.confidence,
                                        )
                                    }
                                    partialText = ""
                                    val startT = result.startTime
                                    val endT = result.endTime
                                    if (startT != null && endT != null) {
                                        val delta = (
                                            (endT - startT).inWholeMilliseconds / 1000.0
                                            ).coerceAtLeast(0.0)
                                        totalDurationSeconds += delta
                                        totalCost += (delta / 60.0) * WHISPER_PRICE_PER_MINUTE_USD
                                    }
                                }
                                is TranscriptionResult.Error -> error = result.message
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(e) { "File transcription error" }
                        error = "Transcription error: ${e.message ?: "Unknown"}"
                    } finally {
                        isTranscribing = false
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AudioFileReadError.UnsupportedFormat) {
                error = "Unsupported file format. Use WAV, AIFF, AU, or MP3."
                isTranscribing = false
            } catch (e: Exception) {
                val cls = e::class.simpleName ?: "Exception"
                logger.error(e) { "Picker / file error" }
                error = "[$cls] ${e.message ?: "Unknown error"}"
                isTranscribing = false
            } finally {
                isPicking = false
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= SampleDimens.WideBreakpoint

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
                ) {
                    FileControlsPanel(
                        selectedFileName = selectedFileName,
                        isTranscribing = isTranscribing,
                        isPicking = isPicking,
                        apiKeyBlank = apiKey.isBlank(),
                        totalDurationSeconds = totalDurationSeconds,
                        totalCost = totalCost,
                        error = error,
                        onPick = ::pickAndTranscribe,
                    )
                }
                TranscriptPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    finalSegments = finalSegments,
                    partialText = partialText,
                    isActive = isTranscribing,
                    emptyHint = if (selectedFileName == null) {
                        "Pick an audio file to transcribe."
                    } else {
                        "Awaiting transcription results…"
                    },
                    listState = listState,
                    transcriptCache = transcriptCache,
                    onClear = {
                        finalSegments = emptyList()
                        partialText = ""
                        totalDurationSeconds = 0.0
                        totalCost = 0.0
                    },
                    showTotals = finalSegments.isNotEmpty() ||
                        isTranscribing ||
                        totalDurationSeconds > 0.0 ||
                        totalCost > 0.0,
                    totalDurationSeconds = totalDurationSeconds,
                    totalCost = totalCost,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(SampleDimens.SectionGap),
            ) {
                FileControlsPanel(
                    selectedFileName = selectedFileName,
                    isTranscribing = isTranscribing,
                    isPicking = isPicking,
                    apiKeyBlank = apiKey.isBlank(),
                    totalDurationSeconds = totalDurationSeconds,
                    totalCost = totalCost,
                    error = error,
                    onPick = ::pickAndTranscribe,
                )
                TranscriptPanel(
                    modifier = Modifier.weight(1f),
                    finalSegments = finalSegments,
                    partialText = partialText,
                    isActive = isTranscribing,
                    emptyHint = if (selectedFileName == null) {
                        "Pick an audio file to transcribe."
                    } else {
                        "Awaiting transcription results…"
                    },
                    listState = listState,
                    transcriptCache = transcriptCache,
                    onClear = {
                        finalSegments = emptyList()
                        partialText = ""
                        totalDurationSeconds = 0.0
                        totalCost = 0.0
                    },
                    showTotals = finalSegments.isNotEmpty() ||
                        isTranscribing ||
                        totalDurationSeconds > 0.0 ||
                        totalCost > 0.0,
                    totalDurationSeconds = totalDurationSeconds,
                    totalCost = totalCost,
                )
            }
        }
    }
}

@Composable
private fun FileControlsPanel(
    selectedFileName: String?,
    isTranscribing: Boolean,
    isPicking: Boolean,
    apiKeyBlank: Boolean,
    totalDurationSeconds: Double,
    totalCost: Double,
    error: String?,
    onPick: () -> Unit,
) {
    SamplePanel {
        SamplePanelLabel("File source")
        Text(
            selectedFileName ?: "No file selected",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
        Text(
            "WAV · AIFF · AU · MP3",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(visible = isTranscribing, enter = fadeIn(), exit = fadeOut()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                SampleStatusText("Streaming chunks…")
            }
        }

        if (totalDurationSeconds > 0.0 || totalCost > 0.0) {
            SampleMetaRow("audio_s", formatDecimal(totalDurationSeconds, 1))
            SampleMetaRow("est_usd", "$${formatDecimal(totalCost, 4)}")
        }

        error?.let { SampleErrorBanner(it) }

        Button(
            onClick = onPick,
            enabled = !isTranscribing && !isPicking && !apiKeyBlank,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(SampleIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isPicking) "Picking…" else "Choose file")
        }
    }
}

@Composable
private fun TranscriptPanel(
    modifier: Modifier = Modifier,
    finalSegments: List<TranscriptionSegment>,
    partialText: String,
    isActive: Boolean,
    emptyHint: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    transcriptCache: TranscriptCache?,
    onClear: () -> Unit,
    showTotals: Boolean = false,
    totalDurationSeconds: Double = 0.0,
    totalCost: Double = 0.0,
) {
    SamplePanel(modifier = modifier, fillMaxHeight = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SamplePanelLabel("Transcript")
                SampleStatusText("${finalSegments.size} segment${if (finalSegments.size == 1) "" else "s"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (transcriptCache != null && finalSegments.isNotEmpty()) {
                    TextButton(onClick = { transcriptCache.revealInFileExplorer() }) {
                        Text("Folder")
                    }
                }
                if (finalSegments.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }

        if (showTotals && (totalDurationSeconds > 0.0 || totalCost > 0.0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SampleStatusText("${formatDecimal(totalDurationSeconds, 1)}s")
                SampleStatusText("$${formatDecimal(totalCost, 4)}")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(finalSegments) { segment ->
                    TranscriptionSegmentItem(segment)
                }

                if (partialText.isNotBlank()) {
                    item {
                        Text(
                            text = partialText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }

                if (finalSegments.isEmpty() && partialText.isBlank() && !isActive) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emptyHint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptionSegmentItem(segment: TranscriptionSegment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val confidenceColor = when {
            segment.confidence >= 0.9f -> SampleColors.ConfidenceHigh
            segment.confidence >= 0.7f -> SampleColors.ConfidenceMid
            else -> SampleColors.ConfidenceLow
        }

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(confidenceColor),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "conf=${formatDecimal(segment.confidence.toDouble(), 2)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Represents a finalized transcription segment.
 */
data class TranscriptionSegment(
    val text: String,
    val confidence: Float,
)
