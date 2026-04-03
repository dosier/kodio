package space.kodio.core.io.files.wav

import kotlinx.io.*
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileReadError
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

/**
 * Reads a RIFF/WAVE file from [from] and returns an [AudioSource] containing
 * the decoded PCM data and its [AudioFormat].
 *
 * Supports PCM-Int (format code 1) and IEEE-Float (format code 3).
 * Tolerates unknown chunks between `fmt ` and `data` (e.g. LIST, INFO, JUNK).
 */
internal fun readWav(from: Source): AudioSource {
    // --- RIFF header ---
    val riffId = try {
        from.readString(4)
    } catch (e: Exception) {
        throw AudioFileReadError.InvalidFile("Cannot read RIFF header: file is too short or unreadable.")
    }
    if (riffId != "RIFF")
        throw AudioFileReadError.InvalidFile("Not a RIFF file (got '$riffId').")

    from.readIntLe() // file size minus 8 — not needed for parsing

    val waveId = from.readString(4)
    if (waveId != "WAVE")
        throw AudioFileReadError.InvalidFile("Not a WAVE file (got '$waveId').")

    // --- Chunk-scanning loop ---
    var audioFormatCode = -1
    var numChannels = -1
    var sampleRate = -1
    var bitsPerSample = -1
    var fmtFound = false

    while (true) {
        val chunkId = try {
            from.readString(4)
        } catch (_: Exception) {
            throw AudioFileReadError.InvalidFile("Unexpected end of file before 'data' chunk.")
        }
        val chunkSize = from.readIntLe().toLong()

        when (chunkId) {
            "fmt " -> {
                audioFormatCode = from.readShortLe().toInt() and 0xFFFF
                numChannels = from.readShortLe().toInt() and 0xFFFF
                sampleRate = from.readIntLe()
                from.readIntLe()  // byteRate — derived, not stored
                from.readShortLe() // blockAlign — derived, not stored
                bitsPerSample = from.readShortLe().toInt() and 0xFFFF

                // Skip any extra fmt bytes (e.g. cbSize in extended format)
                val extraBytes = chunkSize - 16
                if (extraBytes > 0) from.skip(extraBytes)

                fmtFound = true
            }

            "data" -> {
                if (!fmtFound)
                    throw AudioFileReadError.InvalidFile("'data' chunk found before 'fmt ' chunk.")

                val encoding: SampleEncoding = when (audioFormatCode) {
                    1 -> PcmInt(
                        bitDepth = when (bitsPerSample) {
                            8 -> IntBitDepth.Eight
                            16 -> IntBitDepth.Sixteen
                            24 -> IntBitDepth.TwentyFour
                            32 -> IntBitDepth.ThirtyTwo
                            else -> throw AudioFileReadError.UnsupportedFormat(
                                "Unsupported PCM bit depth: $bitsPerSample"
                            )
                        },
                        endianness = Endianness.Little,
                        layout = SampleLayout.Interleaved,
                        signed = bitsPerSample != 8 // WAV convention: 8-bit is unsigned
                    )

                    3 -> PcmFloat(
                        precision = when (bitsPerSample) {
                            32 -> FloatPrecision.F32
                            64 -> FloatPrecision.F64
                            else -> throw AudioFileReadError.UnsupportedFormat(
                                "Unsupported float precision: $bitsPerSample-bit"
                            )
                        },
                        layout = SampleLayout.Interleaved
                    )

                    else -> throw AudioFileReadError.UnsupportedFormat(
                        "Unsupported WAV audio format code: $audioFormatCode"
                    )
                }

                val format = AudioFormat(
                    sampleRate = sampleRate,
                    channels = Channels.fromInt(numChannels),
                    encoding = encoding
                )

                val buffer = Buffer()
                from.readTo(buffer, chunkSize)
                return AudioSource.of(format, buffer)
            }

            else -> {
                // Skip unknown chunks (LIST, INFO, JUNK, bext, etc.)
                from.skip(chunkSize)
            }
        }
    }
}