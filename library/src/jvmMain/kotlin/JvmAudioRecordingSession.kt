import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
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
        val audioFormat = format.toJvmAudioFormat()
        val mixerInfo = JvmAudioSystem.getMixerInfo().firstOrNull { it.name == device.id }
        if (mixerInfo == null)
            throw JvmAudioException.DeviceNotFound(device)
        val mixer = JvmAudioSystem.getMixer(mixerInfo)
        val lineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)
        try {
            val line = mixer.getLine(lineInfo) as TargetDataLine
            this.dataLine = line
            line.open(audioFormat)
            line.start()
            return format
        } catch (e: LineUnavailableException) {
            throw JvmAudioException.LineNotAvailable(e)
        }
    }

    override suspend fun startRecording(channel: SendChannel<ByteArray>) {
        val line = dataLine ?: return
        val buffer = ByteArray(line.bufferSize / 5)
        while (coroutineContext.isActive && line.isOpen) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0)
                channel.send(buffer.copyOf(bytesRead))
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