import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import space.kodio.core.AudioPlaybackSession
import space.kodio.core.DefaultRecordingInt16
import space.kodio.core.SystemAudioSystem
import space.kodio.core.enumerateDevices
import space.kodio.core.io.collectAsSource
import space.kodio.core.io.convertAudio
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToFile
import kotlin.time.Duration.Companion.seconds

fun main(): kotlin.Unit = runBlocking {
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

    println(flow.format)
    println(flow.map { it.size }.toList().sum())
    println(flow.collectAsSource().byteCount)

    flow.writeToFile(
        format = AudioFileFormat.Wav,
        path = Path("test.wav")
    )

//    val playback = SystemAudioSystem.createPlaybackSession(speaker)
//    playback.load(flow)
//    playback.play()
//    delay(5.seconds)
//    println("Playback finished")
}