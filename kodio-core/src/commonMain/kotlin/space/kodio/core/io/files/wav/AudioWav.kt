package space.kodio.core.io.files.wav

import kotlinx.io.Sink
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileWriteError

/**
 * Writes a RIFF/WAVE file (PCM-Int or IEEE-Float).
 * Assumptions:
 *  - Data is INTERLEAVED (not planar)
 *  - Little-endian samples (WAV requires LE)
 *  - from.byteCount is known
 */
internal fun writeWav(from: AudioSource, to: Sink) {
    val format = from.format

    // --- Validate WAV constraints ---
    when (val enc = format.encoding) {
        is PcmInt -> {
            if (enc.layout == SampleLayout.Planar)
                throw AudioFileWriteError.UnsupportedFormat("WAV writer requires interleaved PCM-Int.")
            if (enc.endianness != Endianness.Little)
                throw AudioFileWriteError.UnsupportedFormat("WAV requires little-endian PCM-Int.")
        }
        is PcmFloat -> {
            if (enc.layout == SampleLayout.Planar)
                throw AudioFileWriteError.UnsupportedFormat("WAV writer requires interleaved PCM-Float.")
            // WAV float is also little-endian by convention
        }
    }

    // --- Derive header fields ---
    val numChannels = format.channels.count
    val sampleRate  = format.sampleRate

    val (audioFormatCode, bitsPerSample) = when (val enc = format.encoding) {
        is PcmInt -> 1 to enc.bitDepth.bits
        is PcmFloat -> 3 to when (enc.precision) {
            FloatPrecision.F32 -> 32
            FloatPrecision.F64 -> 64
        }
    }

    val bytesPerSample = bitsPerSample / 8
    val blockAlign = numChannels * bytesPerSample              // bytes per frame (all channels)
    val byteRate   = sampleRate * blockAlign                   // bytes per second

    val dataSize   = from.byteCount
    val riffSize   = 36 + dataSize                             // 4+ (fmt) 24 + (data) 8 + dataSize, RIFF wants fileSize-8

    // --- Write RIFF/WAVE header ---
    with(to) {
        // RIFF chunk
        writeString("RIFF")
        writeIntLe(riffSize.toInt())
        writeString("WAVE")

        // fmt  subchunk (PCM/IEEE-float uses 16-byte fmt chunk)
        writeString("fmt ")
        writeIntLe(16)                                 // Subchunk1Size
        writeShortLe(audioFormatCode.toShort())        // AudioFormat: 1=PCM, 3=IEEE float
        writeShortLe(numChannels.toShort())            // NumChannels
        writeIntLe(sampleRate)                         // SampleRate
        writeIntLe(byteRate)                           // ByteRate
        writeShortLe(blockAlign.toShort())             // BlockAlign
        writeShortLe(bitsPerSample.toShort())          // BitsPerSample

        // data subchunk header
        writeString("data")
        writeIntLe(dataSize.toInt())
    }

    // --- Write payload ---
    to.write(from.source, from.byteCount)
}