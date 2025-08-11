package space.kodio.core.io.files.wav

import kotlinx.io.Sink
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import space.kodio.core.Encoding
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileWriteError

internal fun writeWav(from: AudioSource, to: Sink) {
    // Get properties from the audio format
    val format = from.format
    val numChannels = format.channels.count
    val bitsPerSample = format.bitDepth.value
    val sampleRate = format.sampleRate

    // --- WAV Header Calculations ---

    // 1 for PCM. TODO: The official spec also defines 3 for IEEE Float.
    val audioFormatCode = when (format.encoding) {
        is Encoding.Pcm -> 1
        else -> throw AudioFileWriteError.UnsupportedFormat("Unsupported encoding for WAV file: ${format.encoding}")
    }

    // The size of one "frame" of audio (all channels for one sample point)
    val blockAlign = numChannels * bitsPerSample / 8
    // The number of bytes per second
    val byteRate = sampleRate * blockAlign

    // Size of the actual audio data payload in bytes
    val subChunk2Size = from.byteCount
    // Total file size minus the first 8 bytes ("RIFF" and the size field itself)
    val chunkSize = 36 + subChunk2Size

    // --- Write WAV Header to Buffer ---
    with(to) {
        // -- RIFF Chunk Descriptor --
        writeString("RIFF") // ChunkID: Contains the letters "RIFF"
        writeIntLe(chunkSize.toInt()) // ChunkSize: 36 + SubChunk2Size
        writeString("WAVE") // Format: Contains the letters "WAVE"

        // -- "fmt " Sub-Chunk --
        writeString("fmt ") // Subchunk1ID: Contains "fmt " (note the space)
        writeIntLe(16) // Subchunk1Size: 16 for PCM
        writeShortLe(audioFormatCode.toShort()) // AudioFormat: 1 for PCM
        writeShortLe(numChannels.toShort()) // NumChannels: Mono = 1, Stereo = 2
        writeIntLe(sampleRate) // SampleRate: e.g., 44100, 48000
        writeIntLe(byteRate) // ByteRate: SampleRate * NumChannels * BitsPerSample/8
        writeShortLe(blockAlign.toShort()) // BlockAlign: NumChannels * BitsPerSample/8
        writeShortLe(bitsPerSample.toShort()) // BitsPerSample: 8, 16, 24, etc.

        // -- "data" Sub-Chunk --
        writeString("data") // Subchunk2ID: Contains "data"
        writeIntLe(subChunk2Size.toInt()) // Subchunk2Size: Size of the audio data
    }

    // --- Write Actual Audio Data ---
    to.write(from.source, from.byteCount)
}