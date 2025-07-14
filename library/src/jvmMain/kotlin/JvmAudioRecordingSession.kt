import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioSystem as JvmAudioSystem
import kotlin.coroutines.coroutineContext

/**
 * JVM implementation for [AudioRecordingSession].
 *
 * @param device The input device to record from.
 * @param format The format of the audio data.
 */
class JvmAudioRecordingSession(
    private val device: AudioDevice.Input,
    private val format: AudioFormat = DefaultJvmRecordingAudioFormat
) : BaseAudioRecordingSession() {

    private var dataLine: TargetDataLine? = null

    override suspend fun prepareRecording(): AudioFormat {
        val mixer = getMixer(device)
        val audioFormat = format
            .takeIf { mixer.isSupported<TargetDataLine>(it) }
            ?: device.formatSupport.defaultFormat
        val line = mixer.getLine<TargetDataLine>(audioFormat)
        line.open(audioFormat)
        line.start()
        this.dataLine = line
        return format
    }
    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val line = dataLine ?: return
        val buffer = ByteArray(line.bufferSize / 5)
        while (coroutineContext.isActive && line.isOpen) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                println("read $bytesRead bytes from line (sample = ${buffer.take(10)})")
                channel.send(buffer.copyOf(bytesRead))
            }
        }
    }

    override fun onCleanup() {
        dataLine?.run {
            if (isOpen) {
                stop()
                close()
            }
        }
        dataLine = null
    }
}
