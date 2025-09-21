package space.kodio.core.io

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioRecordingSession

private const val IDLE_FLAG = 1.toByte()
private const val RECORDING_FLAG = 2.toByte()
private const val STOPPED_FLAG = 3.toByte()
private const val ERROR_FLAG = 4.toByte()

fun AudioRecordingSession.State.encodeToByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeAudioRecordingState(this)
    return buffer.readByteArray()
}

fun ByteArray.decodeAsAudioRecordingState(): AudioRecordingSession.State {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readAudioRecordingState()
}

fun Buffer.writeAudioRecordingState(state: AudioRecordingSession.State) {
    when (state) {
        AudioRecordingSession.State.Idle -> {
            writeByte(IDLE_FLAG)
        }
        is AudioRecordingSession.State.Recording -> {
            writeByte(RECORDING_FLAG)
        }
        AudioRecordingSession.State.Stopped -> {
            writeByte(STOPPED_FLAG)
        }
        is AudioRecordingSession.State.Error -> {
            writeByte(ERROR_FLAG)
            writeUtf8(state.error.message?:"unknown error")
        }
    }
}

fun Buffer.readAudioRecordingState(): AudioRecordingSession.State {
    return when (val flag = readByte()) {
        IDLE_FLAG -> AudioRecordingSession.State.Idle
        RECORDING_FLAG -> AudioRecordingSession.State.Recording
        STOPPED_FLAG -> AudioRecordingSession.State.Stopped
        ERROR_FLAG -> {
            val errorMessage = readUtf8()
            AudioRecordingSession.State.Error(Throwable(errorMessage))
        }
        else -> error("Unknown flag: $flag")
    }
}