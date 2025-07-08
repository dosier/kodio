import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RecordingSessionTest {

    @Test
    fun create() {
        runTest {
            println("Hello, JS!")
            val a = SystemAudioSystem.listInputDevices().first()
            val s = SystemAudioSystem.createRecordingSession(a)

            s.start()
            s.audioDataFlow.collect {
                println(it)
            }
        }
    }
}