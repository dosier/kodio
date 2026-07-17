package space.kodio.core.io.files.aiff

import kotlinx.io.*
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import space.kodio.core.*
import space.kodio.core.SampleEncoding.PcmFloat
import space.kodio.core.SampleEncoding.PcmInt
import space.kodio.core.io.AudioSource
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReadError
import space.kodio.core.io.files.AudioFileWriteError
import space.kodio.core.io.files.metadata.AudioEncodingInfo
import space.kodio.core.io.files.metadata.AudioFileMetadata
import space.kodio.core.io.files.metadata.AudioTags
import space.kodio.core.io.readExtended80BitFloat
import space.kodio.core.io.readExactly
import space.kodio.core.io.readIntBe
import space.kodio.core.io.readUInt16Be
import space.kodio.core.io.skipChunkPadded
import space.kodio.core.io.swapEndianness
import space.kodio.core.io.writeExtended80BitFloat
import space.kodio.core.io.writeIntBe
import space.kodio.core.io.writeUInt16Be

private const val COMM_SIZE_AIFF = 18L
private const val COMM_SIZE_AIFC = 22L

internal fun writeAiff(from: AudioSource, to: Sink) {
    when (val enc = from.format.encoding) {
        is PcmFloat -> writeAifc(from, to, enc)
        is PcmInt -> writeClassicAiff(from, to, enc)
    }
}

private fun writeClassicAiff(from: AudioSource, to: Sink, enc: PcmInt) {
    val format = from.format
    if (enc.layout == SampleLayout.Planar)
        throw AudioFileWriteError.UnsupportedFormat("AIFF writer requires interleaved PCM-Int.")
    if (enc.endianness != Endianness.Little)
        throw AudioFileWriteError.UnsupportedFormat("AIFF writer expects little-endian PCM-Int input.")
    if (!enc.packed)
        throw AudioFileWriteError.UnsupportedFormat("AIFF writer requires packed PCM-Int.")
    if (enc.bitDepth == IntBitDepth.Eight && !enc.signed)
        throw AudioFileWriteError.UnsupportedFormat("AIFF 8-bit PCM is signed; unsigned input is not supported.")

    val bitsPerSample = enc.bitDepth.bits
    val bytesPerSample = bitsPerSample / 8
    val numSampleFrames = frameCount(from)
    val payload = loadAndSwapPayload(from, bytesPerSample)
    val ssndChunkDataSize = 8 + payload.size
    val ssndPad = ssndChunkDataSize and 1
    val formBodySize = 4 + (8 + COMM_SIZE_AIFF.toInt()) + (8 + ssndChunkDataSize + ssndPad)

    with(to) {
        writeString("FORM")
        writeIntBe(formBodySize)
        writeString("AIFF")
        writeString("COMM")
        writeIntBe(COMM_SIZE_AIFF.toInt())
        writeUInt16Be(format.channels.count)
        writeIntBe(numSampleFrames)
        writeUInt16Be(bitsPerSample)
        write(writeExtended80BitFloat(format.sampleRate.toDouble()))
        writeString("SSND")
        writeIntBe(ssndChunkDataSize)
        writeIntBe(0)
        writeIntBe(0)
    }
    to.write(payload)
    if (ssndPad == 1) to.write(byteArrayOf(0))
}

private fun writeAifc(from: AudioSource, to: Sink, enc: PcmFloat) {
    val format = from.format
    if (enc.layout == SampleLayout.Planar)
        throw AudioFileWriteError.UnsupportedFormat("AIFF-C writer requires interleaved PCM-Float.")

    val compressionType = when (enc.precision) {
        FloatPrecision.F32 -> "fl32"
        FloatPrecision.F64 -> "fl64"
    }
    val bitsPerSample = when (enc.precision) {
        FloatPrecision.F32 -> 32
        FloatPrecision.F64 -> 64
    }
    val bytesPerSample = bitsPerSample / 8
    val numSampleFrames = frameCount(from)
    val payload = loadAndSwapPayload(from, bytesPerSample)
    val ssndChunkDataSize = 8 + payload.size
    val ssndPad = ssndChunkDataSize and 1
    val formBodySize = 4 + (8 + COMM_SIZE_AIFC.toInt()) + (8 + ssndChunkDataSize + ssndPad)

    with(to) {
        writeString("FORM")
        writeIntBe(formBodySize)
        writeString("AIFC")
        writeString("COMM")
        writeIntBe(COMM_SIZE_AIFC.toInt())
        writeUInt16Be(format.channels.count)
        writeIntBe(numSampleFrames)
        writeUInt16Be(bitsPerSample)
        write(writeExtended80BitFloat(format.sampleRate.toDouble()))
        writeString(compressionType)
        writeString("SSND")
        writeIntBe(ssndChunkDataSize)
        writeIntBe(0)
        writeIntBe(0)
    }
    to.write(payload)
    if (ssndPad == 1) to.write(byteArrayOf(0))
}

private fun frameCount(from: AudioSource): Int {
    val format = from.format
    if (from.byteCount % format.bytesPerFrame != 0L)
        throw AudioFileWriteError.UnsupportedFormat("Audio byte count is not aligned to frame size.")
    val framesLong = from.byteCount / format.bytesPerFrame
    if (framesLong > 0xFFFFFFFFL)
        throw AudioFileWriteError.UnsupportedFormat("AIFF frame count does not fit in 32 bits.")
    return (framesLong and 0xFFFFFFFFL).toInt()
}

private fun loadAndSwapPayload(from: AudioSource, bytesPerSample: Int): ByteArray {
    val pcmBytes = try {
        Buffer().apply { write(from.source, from.byteCount) }.readByteArray()
    } catch (e: Exception) {
        throw AudioFileWriteError.IO(e)
    }
    return pcmBytes.swapEndianness(bytesPerSample)
}

internal fun readAiff(from: Source): AudioSource {
    val result = scanAiff(from, loadData = true)
    return result.audioSource
        ?: throw AudioFileReadError.InvalidFile("No SSND chunk found in AIFF file.")
}

internal fun readAiffMetadata(from: Source): AudioFileMetadata {
    val result = scanAiff(from, loadData = false)
    return result.toFileMetadata()
}

private data class AiffScanResult(
    val formType: String,
    val numChannels: Int,
    val numSampleFrames: Long,
    val bitsPerSample: Int,
    val sampleRate: Int,
    val encoding: SampleEncoding,
    val tags: AudioTags,
    val audioSource: AudioSource?,
) {
    fun toFileMetadata(): AudioFileMetadata {
        val sampleEncoding = when (encoding) {
            is PcmInt -> AudioEncodingInfo.Pcm(encoding)
            is PcmFloat -> AudioEncodingInfo.Pcm(encoding)
        }
        val bytesPerFrame = numChannels * (bitsPerSample / 8)
        val duration = if (bytesPerFrame > 0 && numSampleFrames > 0) {
            (numSampleFrames * 1000L / sampleRate).milliseconds
        } else {
            null
        }
        return AudioFileMetadata(
            container = AudioFileFormat.Aiff,
            sampleRate = sampleRate,
            channels = Channels.fromInt(numChannels),
            encoding = sampleEncoding,
            duration = duration,
            tags = tags,
        )
    }
}

private fun scanAiff(from: Source, loadData: Boolean): AiffScanResult {
    val formId: String
    val formSize: Long
    val formType: String
    try {
        formId = from.readString(4)
        formSize = from.readIntBe().toLong() and 0xFFFFFFFFL
        formType = from.readString(4)
    } catch (e: Exception) {
        throw AudioFileReadError.InvalidFile("Cannot read AIFF FORM header: file is too short or unreadable.", cause = e)
    }
    if (formId != "FORM")
        throw AudioFileReadError.InvalidFile("Not a FORM file (got '$formId').")
    if (formType != "AIFF" && formType != "AIFC")
        throw AudioFileReadError.InvalidFile("Not an AIFF/AIFC file (got '$formType').")

    val isAifc = formType == "AIFC"
    var commFound = false
    var numChannels = -1
    var numSampleFrames = -1L
    var bitsPerSample = -1
    lateinit var rateBytes: ByteArray
    var compressionType: String? = null
    val textChunks = mutableMapOf<String, String>()
    var innerRemaining = formSize - 4L

    while (innerRemaining >= 8L) {
        val chunkId: String
        val chunkSize: Long
        try {
            chunkId = from.readString(4)
            chunkSize = from.readIntBe().toLong() and 0xFFFFFFFFL
        } catch (e: Exception) {
            throw AudioFileReadError.InvalidFile("Unexpected end of file inside AIFF container.", cause = e)
        }
        innerRemaining -= 8L
        if (chunkSize > innerRemaining)
            throw AudioFileReadError.InvalidFile("AIFF chunk '$chunkId' extends past FORM data.")

        when (chunkId) {
            "COMM" -> {
                val expectedSize = if (isAifc) COMM_SIZE_AIFC else COMM_SIZE_AIFF
                if (chunkSize != expectedSize)
                    throw AudioFileReadError.InvalidFile("COMM chunk must be $expectedSize bytes, got $chunkSize.")
                try {
                    numChannels = from.readUInt16Be()
                    numSampleFrames = from.readIntBe().toLong() and 0xFFFFFFFFL
                    bitsPerSample = from.readUInt16Be()
                    rateBytes = from.readExactly(10)
                    if (isAifc) {
                        compressionType = from.readString(4)
                    }
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Failed to read COMM chunk.", cause = e)
                }
                if (numChannels <= 0 || numChannels > 2)
                    throw AudioFileReadError.UnsupportedFormat("AIFF channel count $numChannels is not supported (only 1 or 2).")
                if (numSampleFrames > Int.MAX_VALUE.toLong())
                    throw AudioFileReadError.UnsupportedFormat("COMM numSampleFrames is too large.")
                commFound = true
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) {
                    from.skip(1)
                    innerRemaining -= 1L
                }
            }
            "SSND" -> {
                if (!commFound)
                    throw AudioFileReadError.InvalidFile("'SSND' chunk found before 'COMM' chunk.")
                if (chunkSize < 8L)
                    throw AudioFileReadError.InvalidFile("SSND chunk too small.")

                val offset: Long
                val blockSize: Long
                try {
                    offset = from.readIntBe().toLong() and 0xFFFFFFFFL
                    blockSize = from.readIntBe().toLong() and 0xFFFFFFFFL
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Failed to read SSND chunk header.", cause = e)
                }
                if (blockSize != 0L)
                    throw AudioFileReadError.UnsupportedFormat("Non-zero SSND blockSize is not supported.")
                val waveBytes = chunkSize - 8L
                if (offset > waveBytes)
                    throw AudioFileReadError.InvalidFile("SSND offset exceeds chunk sound data.")
                val pcmLength = (waveBytes - offset).toInt()
                if (pcmLength.toLong() != waveBytes - offset)
                    throw AudioFileReadError.UnsupportedFormat("SSND PCM length is too large.")

                val sampleRateDouble = try {
                    readExtended80BitFloat(rateBytes)
                } catch (e: AudioFileReadError) {
                    throw e
                } catch (e: Exception) {
                    throw AudioFileReadError.InvalidFile("Invalid COMM sample rate: ${e.message}", cause = e)
                }
                if (!sampleRateDouble.isFinite() || sampleRateDouble <= 0.0)
                    throw AudioFileReadError.InvalidFile("Invalid COMM sample rate.")

                val sampleRate = sampleRateDouble.roundToInt()
                val encoding = aiffEncoding(isAifc, compressionType, bitsPerSample)
                val bytesPerSample = bitsPerSample / 8

                val audioSource = if (loadData) {
                    val rawPcm: ByteArray
                    try {
                        if (offset > 0L) from.skip(offset)
                        val buf = Buffer()
                        from.readTo(buf, pcmLength.toLong())
                        rawPcm = buf.readByteArray()
                        if (chunkSize and 1L == 1L) from.skip(1)
                    } catch (e: Exception) {
                        throw AudioFileReadError.IO(e)
                    }

                    val expectedFrames = if (bytesPerSample > 0) rawPcm.size / (bytesPerSample * numChannels) else 0
                    if (rawPcm.size % (bytesPerSample * numChannels) != 0)
                        throw AudioFileReadError.InvalidFile("SSND PCM size is not aligned to frames.")
                    if (expectedFrames.toLong() != numSampleFrames)
                        throw AudioFileReadError.InvalidFile(
                            "SSND frame count mismatch: COMM says $numSampleFrames, data implies $expectedFrames."
                        )
                    val pcmLe = rawPcm.swapEndianness(bytesPerSample)
                    val format = AudioFormat(
                        sampleRate = sampleRate,
                        channels = Channels.fromInt(numChannels),
                        encoding = encoding,
                    )
                    AudioSource.of(format, Buffer().apply { write(pcmLe) })
                } else {
                    from.skip(waveBytes - offset)
                    if (chunkSize and 1L == 1L) from.skip(1)
                    null
                }

                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) innerRemaining -= 1L

                return AiffScanResult(
                    formType = formType,
                    numChannels = numChannels,
                    numSampleFrames = numSampleFrames,
                    bitsPerSample = bitsPerSample,
                    sampleRate = sampleRate,
                    encoding = encoding,
                    tags = aiffTextToTags(textChunks),
                    audioSource = audioSource,
                )
            }
            "NAME", "AUTH", "ANNO" -> {
                if (chunkSize <= Int.MAX_VALUE) {
                    val data = from.readExactly(chunkSize.toInt())
                    val text = parseAiffTextChunk(data).trim()
                    if (text.isNotEmpty()) {
                        textChunks[chunkId] = text
                    }
                    if (chunkSize and 1L == 1L) from.skip(1)
                } else {
                    from.skipChunkPadded(chunkSize)
                }
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) innerRemaining -= 1L
            }
            else -> {
                try {
                    from.skipChunkPadded(chunkSize)
                } catch (e: Exception) {
                    throw AudioFileReadError.IO(e)
                }
                innerRemaining -= chunkSize
                if (chunkSize and 1L == 1L) innerRemaining -= 1L
            }
        }
    }
    throw AudioFileReadError.InvalidFile("No SSND chunk found in AIFF file.")
}

private fun aiffEncoding(isAifc: Boolean, compressionType: String?, bitsPerSample: Int): SampleEncoding {
    if (isAifc) {
        return when (compressionType) {
            "NONE" -> aiffPcmIntEncoding(bitsPerSample)
            "fl32" -> {
                if (bitsPerSample != 32)
                    throw AudioFileReadError.InvalidFile("AIFC fl32 expects 32 bits per sample, got $bitsPerSample.")
                PcmFloat(precision = FloatPrecision.F32, layout = SampleLayout.Interleaved)
            }
            "fl64" -> {
                if (bitsPerSample != 64)
                    throw AudioFileReadError.InvalidFile("AIFC fl64 expects 64 bits per sample, got $bitsPerSample.")
                PcmFloat(precision = FloatPrecision.F64, layout = SampleLayout.Interleaved)
            }
            else -> throw AudioFileReadError.UnsupportedFormat(
                "Unsupported AIFC compression type: ${compressionType ?: "null"}"
            )
        }
    }
    return aiffPcmIntEncoding(bitsPerSample)
}

private fun aiffPcmIntEncoding(bitsPerSample: Int): PcmInt {
    val bitDepth = when (bitsPerSample) {
        8 -> IntBitDepth.Eight
        16 -> IntBitDepth.Sixteen
        24 -> IntBitDepth.TwentyFour
        32 -> IntBitDepth.ThirtyTwo
        else -> throw AudioFileReadError.UnsupportedFormat("Unsupported AIFF bit depth: $bitsPerSample")
    }
    return PcmInt(
        bitDepth = bitDepth,
        endianness = Endianness.Little,
        layout = SampleLayout.Interleaved,
        signed = true,
        packed = true,
    )
}
