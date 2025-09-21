package space.kodio.core.io

import kotlinx.io.Buffer
import space.kodio.core.AudioFormatSupport

private const val KNOWN_AUDIO_FORMAT_SUPPORT_FLAG = 1.toByte()
private const val UNKNOWN_AUDIO_FORMAT_SUPPORT_FLAG = 2.toByte()

fun Buffer.writeAudioFormatSupport(audioFormatSupport: AudioFormatSupport) {
    when(audioFormatSupport) {
        is AudioFormatSupport.Known -> {
            writeByte(KNOWN_AUDIO_FORMAT_SUPPORT_FLAG)
            writeAudioFormat(audioFormatSupport.defaultFormat)
            writeInt(audioFormatSupport.supportedFormats.size)
            audioFormatSupport.supportedFormats.forEach { audioFormat ->
                writeAudioFormat(audioFormat)
            }
        }
        AudioFormatSupport.Unknown -> {
            writeByte(UNKNOWN_AUDIO_FORMAT_SUPPORT_FLAG)
        }
    }
}

fun Buffer.readAudioFormatSupport(): AudioFormatSupport {
    return when(val audioFormatSupportFlag = readByte()) {
        KNOWN_AUDIO_FORMAT_SUPPORT_FLAG -> {
            val defaultFormat = readAudioFormat()
            val supportedFormatCount = readInt()
            val supportedFormats = List(supportedFormatCount) {
                readAudioFormat()
            }
            AudioFormatSupport.Known(
                defaultFormat = defaultFormat,
                supportedFormats = supportedFormats
            )
        }
        UNKNOWN_AUDIO_FORMAT_SUPPORT_FLAG -> AudioFormatSupport.Unknown
        else -> error("Unsupported audio format support $audioFormatSupportFlag")
    }
}