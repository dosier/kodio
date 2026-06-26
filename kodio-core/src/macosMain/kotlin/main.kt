import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import space.kodio.core.DefaultRecordingInt16
import space.kodio.core.Kodio
import space.kodio.core.SystemAudioSystem
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToFile
import space.kodio.core.util.namedLogger
import kotlin.time.Duration.Companion.seconds

private val logger = namedLogger("MacosMain")

fun main(): kotlin.Unit = runBlocking {
    Kodio.configureLogging {
        minLevel = LogLevel.Debug
        addWriter(platformLogWriter())
    }
    recordSaveAndPlayback()
}

private suspend fun recordSaveAndPlayback() {
    val useMb = true
    val microphone = SystemAudioSystem.listInputDevices()
        .find { it.name.contains(if (useMb) "MacBook Pro Microphone" else "AirPods Max") }
        ?: error("Could not find microphone")

    val speaker = SystemAudioSystem.listOutputDevices()
        .find { it.name.contains(if (useMb) "MacBook Pro Speakers" else "AirPods Max") }
        ?: error("Could not find speaker")

    val recording = SystemAudioSystem.createRecordingSession(microphone)
    recording.start()
    delay(5.seconds)
    recording.stop()


    val flow = recording.audioFlow.value
        ?: error("Audio flow should not be null")

    logger.info { "${flow.format}" }
    val chunkSizeSum = flow.map { it.size }.toList().sum()
    logger.info { "$chunkSizeSum" }
    val byteCount = flow.collectAsSource().byteCount
    logger.info { "$byteCount" }

    flow.writeToFile(
        format = AudioFileFormat.Wav,
        path = Path("test.wav")
    )

    val playback = SystemAudioSystem.createPlaybackSession(speaker)
    playback.load(flow)
    playback.play()
    delay(5.seconds)
    logger.info { "Playback finished" }
}