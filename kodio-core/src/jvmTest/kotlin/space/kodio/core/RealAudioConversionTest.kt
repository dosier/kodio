package space.kodio.core

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import space.kodio.core.io.convertAudio
import space.kodio.core.io.decode
import space.kodio.core.io.files.wav.readWav
import space.kodio.core.io.files.wav.writeWav
import space.kodio.core.io.AudioSource
import kotlin.math.abs
import kotlin.test.*

/**
 * Integration tests using a real WAV file to verify 24-bit conversion
 * produces correct data (not white noise).
 */
class RealAudioConversionTest {

    private val testWavPath = javaClass.classLoader.getResource("test_sound.wav")!!

    private fun loadTestWav(): Pair<AudioFormat, ByteArray> {
        val bytes = testWavPath.readBytes()
        val source = Buffer().apply { write(bytes) }
        val audioSource = readWav(source)
        val pcmBytes = audioSource.source.readByteArray()
        return audioSource.format to pcmBytes
    }

    @Test
    fun `inspect source WAV format`() {
        val (format, pcmBytes) = loadTestWav()
        println("Source format: $format")
        println("PCM data size: ${pcmBytes.size} bytes")
        println("Bytes per sample: ${format.bytesPerSample}")
        println("Sample count: ${pcmBytes.size / format.bytesPerSample}")

        // Verify it's 16-bit signed LE mono
        val enc = format.encoding as SampleEncoding.PcmInt
        assertEquals(IntBitDepth.Sixteen, enc.bitDepth)
        assertTrue(enc.signed)
        assertEquals(Endianness.Little, enc.endianness)
    }

    @Test
    fun `16bit to 24bit conversion produces correct byte scaling`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()
        val enc = srcFormat.encoding as SampleEncoding.PcmInt
        assertEquals(IntBitDepth.Sixteen, enc.bitDepth, "Test expects 16-bit source")

        val targetFormat = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )

        val inFlow = AudioFlow(srcFormat, flowOf(pcmBytes))
        val outFlow = inFlow.convertAudio(targetFormat)
        val result = outFlow.toList().first()

        println("Source: ${pcmBytes.size} bytes (16-bit)")
        println("Result: ${result.size} bytes (24-bit)")

        // 24-bit should be 1.5x the size of 16-bit
        val expectedSize = (pcmBytes.size / 2) * 3
        assertEquals(expectedSize, result.size, "24-bit data should be 1.5x 16-bit data size")

        // Read first 20 samples from both and compare
        val sampleCount = minOf(20, pcmBytes.size / 2)
        println("\nSample-by-sample comparison (first $sampleCount):")
        println("%-6s  %-10s  %-10s  %-10s  %-5s".format("Index", "16-bit", "24-bit", "Expected24", "OK?"))

        var mismatches = 0
        val totalSamples = pcmBytes.size / 2
        for (i in 0 until totalSamples) {
            val s16 = read16BitSample(pcmBytes, i)
            val s24 = read24BitSample(result, i)
            val expected24 = s16 * 256

            if (i < sampleCount) {
                val ok = s24 == expected24
                println("%-6d  %-10d  %-10d  %-10d  %-5s".format(i, s16, s24, expected24, if (ok) "OK" else "FAIL"))
            }
            if (s24 != expected24) mismatches++
        }

        println("\nTotal samples: $totalSamples, Mismatches: $mismatches")
        assertEquals(0, mismatches, "All 24-bit samples should equal 16-bit * 256")
    }

    @Test
    fun `24bit WAV round-trip - write then read`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        val fmt24 = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )

        // Convert 16-bit -> 24-bit
        val inFlow = AudioFlow(srcFormat, flowOf(pcmBytes))
        val outFlow = inFlow.convertAudio(fmt24)
        val data24 = outFlow.toList().first()

        // Write 24-bit WAV to buffer
        val wavBuffer = Buffer()
        val audioSource = AudioSource.of(fmt24, Buffer().apply { write(data24) })
        writeWav(audioSource, wavBuffer)
        val wavBytes = wavBuffer.readByteArray()

        // Read it back
        val readSource = readWav(Buffer().apply { write(wavBytes) })
        val readFormat = readSource.format
        val readData = readSource.source.readByteArray()

        println("Written format: $fmt24")
        println("Read-back format: $readFormat")
        println("Written data size: ${data24.size}")
        println("Read-back data size: ${readData.size}")

        // Format should match
        assertEquals(fmt24, readFormat, "Round-trip format should match")
        assertContentEquals(data24, readData, "Round-trip data should match")
    }

    @Test
    fun `24bit to Float32 conversion - the actual playback path`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        // Step 1: Convert 16-bit -> 24-bit (what the conversion showcase does)
        val fmt24 = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )
        val data24 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmt24).toList().first()

        // Step 2: Convert 24-bit -> Float32 stereo (what MacosAudioPlaybackSession does)
        val fmtF32Stereo = AudioFormat(
            sampleRate = srcFormat.sampleRate,
            channels = Channels.Stereo,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )
        val dataF32 = AudioFlow(fmt24, flowOf(data24)).convertAudio(fmtF32Stereo).toList().first()

        println("24-bit data: ${data24.size} bytes")
        println("Float32 stereo data: ${dataF32.size} bytes")

        // Verify Float32 data makes sense (not NaN, not all zeros, not random noise)
        val sampleCount = dataF32.size / 4
        var nanCount = 0
        var zeroCount = 0
        var maxAbs = 0f
        var sumAbs = 0.0

        for (i in 0 until sampleCount) {
            val bits = readInt32LE(dataF32, i * 4)
            val f = Float.fromBits(bits)
            if (f.isNaN()) nanCount++
            if (f == 0f) zeroCount++
            val a = abs(f)
            if (a > maxAbs) maxAbs = a
            sumAbs += a
        }

        val avgAbs = sumAbs / sampleCount

        println("Float32 samples: $sampleCount")
        println("NaN count: $nanCount")
        println("Zero count: $zeroCount")
        println("Max |sample|: $maxAbs")
        println("Avg |sample|: $avgAbs")

        assertEquals(0, nanCount, "No NaN values should be present")
        assertTrue(maxAbs <= 1.0f, "All samples should be in [-1, 1] range")
        assertTrue(maxAbs > 0.01f, "Audio should not be silent (max > 0.01)")

        // White noise would have avg ~0.33 for uniform random in [0,1].
        // Real audio (especially short clicks/chips) typically has a lower average
        // and correlation between adjacent samples.
        // Check adjacent sample correlation (white noise has ~0 correlation)
        var corrSum = 0.0
        for (i in 0 until sampleCount - 2 step 2) {
            val f1 = Float.fromBits(readInt32LE(dataF32, i * 4))
            val f2 = Float.fromBits(readInt32LE(dataF32, (i + 2) * 4))
            corrSum += f1.toDouble() * f2.toDouble()
        }
        val correlation = corrSum / (sampleCount / 2)
        println("Adjacent sample correlation: $correlation")
    }

    @Test
    fun `direct 16bit to Float32 - verify decode and encode stages`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        // Decode 16-bit samples to BigDecimal normalized [-1, 1]
        val normalizedSamples = decode(pcmBytes, srcFormat)
        println("Decoded ${normalizedSamples.size} normalized samples from 16-bit")

        // Check first 10 normalized values
        println("\nFirst 10 normalized samples:")
        for (i in 0 until minOf(10, normalizedSamples.size)) {
            val s16 = read16BitSample(pcmBytes, i)
            println("  [$i] 16-bit=$s16, normalized=${normalizedSamples[i]}")
        }

        // Now decode the same data as 24-bit after conversion
        val fmt24 = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )
        val data24 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmt24).toList().first()
        val normalized24 = decode(data24, fmt24)

        println("\nDecoded ${normalized24.size} normalized samples from 24-bit conversion")
        println("\nFirst 10 normalized samples comparison:")
        println("%-6s  %-20s  %-20s  %-10s".format("Index", "From 16-bit", "From 24-bit", "Diff"))

        var maxDiff = 0.0
        for (i in 0 until minOf(10, normalizedSamples.size)) {
            val v16 = normalizedSamples[i].toPlainString()
            val v24 = normalized24[i].toPlainString()
            val diff = (normalizedSamples[i] - normalized24[i]).abs().toPlainString()
            println("%-6d  %-20s  %-20s  %-10s".format(i, v16, v24, diff))
        }

        // Check that re-decoded 24-bit values match original 16-bit values
        for (i in normalizedSamples.indices) {
            val diff = (normalizedSamples[i] - normalized24[i]).abs()
            val d = diff.doubleValue(false)
            if (d > maxDiff) maxDiff = d
        }
        println("\nMax normalization difference: $maxDiff")
        assertTrue(maxDiff < 0.001, "Normalized values from 24-bit should match 16-bit originals closely")
    }

    @Test
    fun `inspect raw 24bit bytes for noise patterns`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        val fmt24 = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )

        val data24 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmt24).toList().first()

        // Check if 24-bit samples have the expected pattern: low byte should always be 0x00
        // because 16-bit * 256 means the least significant byte is always zero
        println("First 30 samples raw bytes (24-bit LE):")
        val sampleCount = minOf(30, data24.size / 3)
        var lowByteNonZero = 0
        val totalSamples = data24.size / 3

        for (i in 0 until sampleCount) {
            val offset = i * 3
            val b0 = data24[offset].toInt() and 0xFF
            val b1 = data24[offset + 1].toInt() and 0xFF
            val b2 = data24[offset + 2].toInt() and 0xFF
            val s16 = if (i < pcmBytes.size / 2) read16BitSample(pcmBytes, i) else "N/A"
            println("  [$i] bytes=[%02X %02X %02X] 24-bit value=%d, source 16-bit=%s".format(
                b0, b1, b2, read24BitSample(data24, i), s16
            ))
        }

        for (i in 0 until totalSamples) {
            val offset = i * 3
            if (data24[offset].toInt() and 0xFF != 0) lowByteNonZero++
        }

        println("\nSamples with non-zero low byte: $lowByteNonZero / $totalSamples")
        assertEquals(0, lowByteNonZero, "Low byte should always be 0x00 for 16-bit*256 conversion")
    }

    @Test
    fun `write 24bit WAV to disk for external verification`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        val fmt24 = srcFormat.copy(
            encoding = SampleEncoding.PcmInt(
                bitDepth = IntBitDepth.TwentyFour,
                endianness = Endianness.Little,
                layout = SampleLayout.Interleaved,
                signed = true,
            )
        )

        val data24 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmt24).toList().first()

        // Write to a temp file
        val outPath = Path("/tmp/kodio_test_24bit.wav")
        val sink = SystemFileSystem.sink(outPath).buffered()
        val audioSource = AudioSource.of(fmt24, Buffer().apply { write(data24) })
        writeWav(audioSource, sink)
        sink.close()

        println("Wrote 24-bit WAV to: $outPath")
        println("Format: $fmt24")
        println("Data size: ${data24.size} bytes")

        // Verify file exists and has reasonable size
        val metadata = SystemFileSystem.metadataOrNull(outPath)
        assertNotNull(metadata, "Output file should exist")
        assertTrue(metadata.size > 44, "WAV file should be larger than header")
        println("File size: ${metadata.size} bytes")
    }

    @Test
    fun `write Float32 WAV to disk for external verification`() = runTest {
        val (srcFormat, pcmBytes) = loadTestWav()

        val fmtF32 = AudioFormat(
            sampleRate = srcFormat.sampleRate,
            channels = srcFormat.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved)
        )

        val dataF32 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmtF32).toList().first()

        val outPath = Path("/tmp/kodio_test_float32.wav")
        val sink = SystemFileSystem.sink(outPath).buffered()
        val audioSource = AudioSource.of(fmtF32, Buffer().apply { write(dataF32) })
        writeWav(audioSource, sink)
        sink.close()

        println("Wrote Float32 WAV to: $outPath")
        println("Data size: ${dataF32.size} bytes")
    }

    // --- Kick.wav tests ---

    private val kickWavPath = javaClass.classLoader.getResource("kick.wav")!!

    private fun loadKickWav(): Pair<AudioFormat, ByteArray> {
        val bytes = kickWavPath.readBytes()
        val source = Buffer().apply { write(bytes) }
        val audioSource = readWav(source)
        val pcmBytes = audioSource.source.readByteArray()
        return audioSource.format to pcmBytes
    }

    @Test
    fun `kick wav - inspect format`() {
        val (format, pcmBytes) = loadKickWav()
        println("Kick.wav format: $format")
        println("PCM data size: ${pcmBytes.size} bytes")
        println("Bytes per sample: ${format.bytesPerSample}")
        println("Bytes per frame: ${format.bytesPerFrame}")
        println("Sample count: ${pcmBytes.size / format.bytesPerSample}")

        assertTrue(pcmBytes.isNotEmpty(), "Kick.wav should have audio data")
        assertTrue(format.sampleRate > 0, "Sample rate should be positive")
    }

    @Test
    fun `kick wav - convert to Float32 for playback`() = runTest {
        val (srcFormat, pcmBytes) = loadKickWav()

        val fmtF32 = AudioFormat(
            sampleRate = srcFormat.sampleRate,
            channels = srcFormat.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved),
        )

        val dataF32 = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(fmtF32).toList().first()

        println("Source: ${pcmBytes.size} bytes ($srcFormat)")
        println("Float32: ${dataF32.size} bytes")

        val sampleCount = dataF32.size / 4
        var nanCount = 0
        var maxAbs = 0f

        for (i in 0 until sampleCount) {
            val bits = readInt32LE(dataF32, i * 4)
            val f = Float.fromBits(bits)
            if (f.isNaN()) nanCount++
            val a = abs(f)
            if (a > maxAbs) maxAbs = a
        }

        println("Float32 samples: $sampleCount, NaN: $nanCount, max |sample|: $maxAbs")

        assertEquals(0, nanCount, "No NaN values should be present")
        assertTrue(maxAbs <= 1.0f, "All samples should be in [-1, 1] range")
        assertTrue(maxAbs > 0.001f, "Audio should not be silent")
    }

    @Test
    fun `kick wav - convert with sample rate change`() = runTest {
        val (srcFormat, pcmBytes) = loadKickWav()
        val targetRate = if (srcFormat.sampleRate == 48000) 44100 else 48000

        val targetFormat = AudioFormat(
            sampleRate = targetRate,
            channels = srcFormat.channels,
            encoding = SampleEncoding.PcmFloat(FloatPrecision.F32, SampleLayout.Interleaved),
        )

        val result = AudioFlow(srcFormat, flowOf(pcmBytes)).convertAudio(targetFormat).toList().first()

        val srcFrames = pcmBytes.size / srcFormat.bytesPerFrame
        val outFrames = result.size / (4 * targetFormat.channels.count)
        val expectedRatio = targetRate.toDouble() / srcFormat.sampleRate
        val actualRatio = outFrames.toDouble() / srcFrames

        println("Source: $srcFrames frames @ ${srcFormat.sampleRate} Hz")
        println("Output: $outFrames frames @ $targetRate Hz")
        println("Expected ratio: $expectedRatio, actual: $actualRatio")

        assertTrue(abs(actualRatio - expectedRatio) < 0.01,
            "Frame count ratio should match sample rate ratio (expected $expectedRatio, got $actualRatio)")

        val sampleCount = result.size / 4
        for (i in 0 until sampleCount) {
            val f = Float.fromBits(readInt32LE(result, i * 4))
            assertFalse(f.isNaN(), "Sample $i should not be NaN")
            assertTrue(abs(f) <= 1.0f, "Sample $i should be in [-1, 1] range, got $f")
        }
    }

    @Test
    fun `kick wav - WAV round-trip preserves data`() = runTest {
        val (srcFormat, pcmBytes) = loadKickWav()

        val wavBuffer = Buffer()
        val audioSource = AudioSource.of(srcFormat, Buffer().apply { write(pcmBytes) })
        writeWav(audioSource, wavBuffer)
        val wavBytes = wavBuffer.readByteArray()

        val readSource = readWav(Buffer().apply { write(wavBytes) })
        val readFormat = readSource.format
        val readData = readSource.source.readByteArray()

        assertEquals(srcFormat, readFormat, "Round-trip format should match")
        assertContentEquals(pcmBytes, readData, "Round-trip data should match")
    }

    // --- Helpers ---

    private fun read16BitSample(data: ByteArray, index: Int): Int {
        val offset = index * 2
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt()
        return (b1 shl 8) or b0
    }

    private fun read24BitSample(data: ByteArray, index: Int): Int {
        val offset = index * 3
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        var u = (b2 shl 16) or (b1 shl 8) or b0
        if (u and 0x800000 != 0) u = u or -0x1000000
        return u
    }

    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}
