package space.kodio.core.io.files.mp3

import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.AudioFileReader
import space.kodio.core.io.files.metadata.AudioEncodingInfo

/**
 * Real-file MP3 checks using:
 * - ISO/IEC Layer III bitstreams from lieff/minimp3 (smoke decode)
 * - LAME-encoded fixtures with ffmpeg PCM references (structural + PSNR report)
 *
 * Hard ISO PSNR > 96 dB is not gated yet: the JavaMP3-based port is not fully
 * conformant. Vectors stay in resources so accuracy can be tightened later.
 */
class Mp3ConformanceTest {

    private val isoVectors = listOf(
        "l3-compl",
        "l3-he_32khz",
        "l3-he_44khz",
        "l3-he_48khz",
        "l3-hecommon",
        "l3-he_mode",
        "l3-si",
        "l3-si_block",
        "l3-si_huff",
        "l3-sin1k0db",
        "l3-nonstandard-id3v1",
        "l3-nonstandard-id3v2",
        "l3-nonstandard-sin1k0db_lame_vbrtag",
    )

    @Test
    fun isoVectorsDecodeToNonEmptyPcm() {
        val failures = mutableListOf<String>()
        for (name in isoVectors) {
            val bit = loadResource("$name.bit")
            val result = try {
                Mp3Decoder.decode(bit)
            } catch (e: Exception) {
                failures += "$name: ${e::class.simpleName}: ${e.message}"
                continue
            }
            if (result.pcm.isEmpty()) failures += "$name: empty PCM"
            if (result.sampleRate !in setOf(32000, 44100, 48000)) {
                failures += "$name: unexpected sampleRate=${result.sampleRate}"
            }
            if (result.channels !in 1..2) {
                failures += "$name: unexpected channels=${result.channels}"
            }
        }
        if (failures.isNotEmpty()) {
            fail("ISO vector decode failures:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun lameMonoFixtureDecodesViaPublicApi() {
        val mp3 = loadResource("lame-mono64-fixture.mp3")
        assertEquals(AudioFileFormat.Mp3, AudioFileFormat.detect(mp3))
        val recording = AudioFileReader.read(mp3, fileName = "clip.mp3")
        assertEquals(44100, recording.format.sampleRate)
        assertTrue(recording.toByteArray().isNotEmpty())
    }

    @Test
    fun lameCbrAndVbrFixturesHaveExpectedFormat() {
        val cbr = Mp3Decoder.decode(loadResource("lame-tone128-cbr.mp3"))
        assertEquals(44100, cbr.sampleRate)
        assertEquals(1, cbr.channels)
        assertTrue(cbr.pcm.size >= 1152 * 2)

        val vbr = Mp3Decoder.decode(loadResource("lame-stereo-vbr.mp3"))
        assertEquals(44100, vbr.sampleRate)
        assertEquals(2, vbr.channels)
        assertTrue(vbr.pcm.size >= 1152 * 4)

        // Track accuracy vs ffmpeg reference (informational soft bound).
        val cbrRef = loadResource("lame-tone128-cbr.pcm")
        val (psnr, _, compared, _) = bestAlignedPsnrS16Le(cbrRef, cbr.pcm)
        assertTrue(compared > 0, "no overlapping samples for CBR PSNR")
        assertTrue(psnr > 0.0, "CBR PSNR should be computable, got $psnr")
    }

    @Test
    fun stereoVbrMetadataReadableWhenId3Present() {
        // Re-encode path may strip tags; prefer ID3 fixture from commonTest path via reader.
        val mp3 = loadResource("lame-stereo-vbr.mp3")
        val meta = AudioFileReader.readMetadata(mp3, fileName = "song.mp3")
        assertEquals(AudioFileFormat.Mp3, meta.container)
        assertTrue(meta.encoding is AudioEncodingInfo.Compressed.Mp3)
        assertEquals(44100, meta.sampleRate)
    }

    private fun loadResource(fileName: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream("mp3-vectors/$fileName")
            ?: error("Missing test resource mp3-vectors/$fileName")
        return stream.use { it.readBytes() }
    }

    companion object {
        private const val MAX_ALIGN_SAMPLES = 2304

        fun bestAlignedPsnrS16Le(reference: ByteArray, decoded: ByteArray): Quad {
            var best = Quad(0.0, Int.MAX_VALUE, 0, 0)
            val maxOff = minOf(MAX_ALIGN_SAMPLES, decoded.size / 2)
            for (off in 0..maxOff) {
                val cur = psnrS16Le(reference, decoded, decodeSampleOffset = off)
                if (cur.psnr > best.psnr) best = cur.copy(offset = off)
                if (best.psnr == Double.POSITIVE_INFINITY) break
            }
            for (off in 1..minOf(MAX_ALIGN_SAMPLES, reference.size / 2)) {
                val cur = psnrS16Le(reference, decoded, refSampleOffset = off)
                if (cur.psnr > best.psnr) best = cur.copy(offset = -off)
            }
            return best
        }

        fun psnrS16Le(
            reference: ByteArray,
            decoded: ByteArray,
            refSampleOffset: Int = 0,
            decodeSampleOffset: Int = 0,
        ): Quad {
            val n = minOf(
                reference.size / 2 - refSampleOffset,
                decoded.size / 2 - decodeSampleOffset,
            )
            if (n <= 0) return Quad(0.0, Int.MAX_VALUE, 0, decodeSampleOffset)
            var sumSq = 0.0
            var maxDiff = 0
            var i = 0
            while (i < n) {
                val ri = (refSampleOffset + i) * 2
                val di = (decodeSampleOffset + i) * 2
                val r = ((reference[ri].toInt() and 0xFF) or (reference[ri + 1].toInt() shl 8)).toShort().toInt()
                val d = ((decoded[di].toInt() and 0xFF) or (decoded[di + 1].toInt() shl 8)).toShort().toInt()
                val diff = r - d
                val abs = if (diff < 0) -diff else diff
                if (abs > maxDiff) maxDiff = abs
                sumSq += diff.toDouble() * diff.toDouble()
                i++
            }
            if (sumSq == 0.0) return Quad(Double.POSITIVE_INFINITY, 0, n, decodeSampleOffset)
            val psnr = 10.0 * log10((32768.0 * 32768.0) / (sumSq / n))
            return Quad(psnr, maxDiff, n, decodeSampleOffset)
        }

        data class Quad(val psnr: Double, val maxDiff: Int, val compared: Int, val offset: Int)
    }
}
