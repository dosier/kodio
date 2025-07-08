import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem
import kotlin.coroutines.coroutineContext

class JvmAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultJvmRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var dataLine: TargetDataLine? = null

    override suspend fun prepareRecording(): AudioFormat {
        val mixerInfo = JvmAudioSystem.getMixerInfo().first { it.name == device.id }
        val mixer = JvmAudioSystem.getMixer(mixerInfo)
        val lineInfo = DataLine.Info(TargetDataLine::class.java, format.toJvmAudioFormat())
        val line = mixer.getLine(lineInfo) as TargetDataLine
        this.dataLine = line
        line.open(format.toJvmAudioFormat())
        line.start()
        line.addLineListener {
            println("${System.currentTimeMillis()}: Line event: ${it.type}")
        }
        return format
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val line = dataLine ?: return
        val buffer = ByteArray(line.bufferSize / 5)
        while (coroutineContext.isActive && line.isOpen) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                channel.send(buffer.copyOf(bytesRead))
            }
        }
    }

    override fun onCleanup() {
        dataLine?.let {
            if (it.isOpen) {
                it.stop()
                it.close()
            }
        }
        dataLine = null
    }
}