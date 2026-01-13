package space.kodio.core.io

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import space.kodio.core.AudioPlaybackSession

private const val IDLE_FLAG = 1.toByte()
private const val READY_FLAG = 2.toByte()
private const val PLAYING_FLAG = 3.toByte()
private const val PAUSED_FLAG = 4.toByte()
private const val FINISHED_FLAG = 5.toByte()
private const val ERROR_FLAG = 6.toByte()

fun AudioPlaybackSession.State.encodeToByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeAudioPlaybackState(this)
    return buffer.readByteArray()
}

fun ByteArray.decodeAsAudioPlaybackState(): AudioPlaybackSession.State {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readAudioPlaybackState()
}

fun Buffer.writeAudioPlaybackState(state: AudioPlaybackSession.State) {
    when (state) {
        AudioPlaybackSession.State.Idle -> {
            writeByte(IDLE_FLAG)
        }
        AudioPlaybackSession.State.Ready -> {
            writeByte(READY_FLAG)
        }
        AudioPlaybackSession.State.Playing -> {
            writeByte(PLAYING_FLAG)
        }
        AudioPlaybackSession.State.Paused -> {
            writeByte(PAUSED_FLAG)
        }
        AudioPlaybackSession.State.Finished -> {
            writeByte(FINISHED_FLAG)
        }
        is AudioPlaybackSession.State.Error -> {
            writeByte(ERROR_FLAG)
            writeUtf8(state.error.message ?: "unknown error")
        }
    }
}

fun Buffer.readAudioPlaybackState(): AudioPlaybackSession.State {
    return when (val flag = readByte()) {
        IDLE_FLAG -> AudioPlaybackSession.State.Idle
        READY_FLAG -> AudioPlaybackSession.State.Ready
        PLAYING_FLAG -> AudioPlaybackSession.State.Playing
        PAUSED_FLAG -> AudioPlaybackSession.State.Paused
        FINISHED_FLAG -> AudioPlaybackSession.State.Finished
        ERROR_FLAG -> {
            val errorMessage = readUtf8()
            AudioPlaybackSession.State.Error(Throwable(errorMessage))
        }
        else -> error("Unknown playback state flag: $flag")
    }
}
