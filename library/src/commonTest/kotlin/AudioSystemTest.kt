import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Test for the JVM implementation of the AudioSystem.
 * This requires a system with at least one active input and output audio device to run.
 */
class AudioSystemTest {

    private lateinit var audioSystem: AudioSystem

    @BeforeTest
    fun setUp() {
        // We get the SystemAudioSystem instance for testing
        audioSystem = SystemAudioSystem
    }

    @Test
    fun `listInputDevices should return available input devices`() = runTest {
        val inputDevices = audioSystem.listInputDevices()
        println("Found ${inputDevices.size} input devices:")
        inputDevices.forEach { println("- ${it.name} (ID: ${it.id})") }

        // This test assumes a system has at least one input device.
        // We use an assertion to skip the test if no devices are found, rather than failing.
        assertTrue(inputDevices.isNotEmpty(), "No input devices found, skipping test.")
    }

    @Test
    fun `listOutputDevices should return available output devices`() = runTest {
        val outputDevices = audioSystem.listOutputDevices()
        println("Found ${outputDevices.size} output devices:")
        outputDevices.forEach { println("- ${it.name} (ID: ${it.id})") }

        // Assumption to skip if no devices are found.
        assertTrue(outputDevices.isNotEmpty(), "No output devices found, skipping test.")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recording and playback session should perform a loopback test`() = runTest(timeout = 10.seconds) {
        // 1. Find an input and output device
        val inputDevice = audioSystem.listInputDevices().firstOrNull()
        val outputDevice = audioSystem.listOutputDevices().firstOrNull()

        assertTrue(inputDevice != null, "No input device available for loopback test.")
        assertTrue(outputDevice != null, "No output device available for loopback test.")

        // 2. Create sessions
        val recordingSession = audioSystem.createRecordingSession(inputDevice)
        val playbackSession = audioSystem.createPlaybackSession(outputDevice)

        // 3. Define the audio format
        val format = inputDevice.formatSupport.defaultFormat

        // 4. Record audio for 2 seconds
        val recordedData = mutableListOf<ByteArray>()
        val recordingStateChanges = mutableListOf<RecordingState>()

        val collectRecordingStateJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            recordingSession.state.toList(recordingStateChanges)
        }
        val collectAudioDataJob = launch {
            recordingSession.audioDataFlow.collect {
                recordedData.add(it)
            }
        }

        assertEquals(RecordingState.Idle, recordingSession.state.value)
        recordingSession.start(format)

        // Wait until recording starts
        withTimeout(1.seconds) {
            recordingSession.state.first { it == RecordingState.Recording }
        }
        assertEquals(RecordingState.Recording, recordingSession.state.value)

        // Let it record for 2 seconds
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            delay(2.seconds)
        }
        recordingSession.stop()

        assertEquals(RecordingState.Stopped, recordingSession.state.value)

        // 5. Play back the recorded audio
        val playbackStateChanges = mutableListOf<PlaybackState>()
        val collectPlaybackStateJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackSession.state.toList(playbackStateChanges)
        }

        assertEquals(PlaybackState.Idle, playbackSession.state.value)

        // Ensure we actually recorded something
        assertTrue(recordedData.isNotEmpty(), "No audio data was recorded.")

        // Play the collected data
        playbackSession.play(recordedData.asFlow().asAudioDataFlow(format))

        // Wait until it's finished playing
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5.seconds) {
                playbackSession.state.first { it == PlaybackState.Finished }
            }
        }

        // 6. Verify state transitions
        assertEquals(listOf(RecordingState.Idle, RecordingState.Recording, RecordingState.Stopped), recordingStateChanges)

        // The last state might be PLAYING if the flow finishes fast
        val expectedPlaybackStates = listOf(PlaybackState.Idle, PlaybackState.Playing, PlaybackState.Finished)
        assertEquals(expectedPlaybackStates, playbackStateChanges)

        // Clean up jobs
        collectRecordingStateJob.cancel()
        collectAudioDataJob.cancel()
        collectPlaybackStateJob.cancel()
    }
}