/*
 * Pure-Kotlin port of lieff/minimp3 (https://github.com/lieff/minimp3).
 * Original work is dedicated to the public domain under CC0 1.0 Universal.
 */
package space.kodio.core.io.files.mp3.minimp3

internal const val SAMPLES_PER_GRANULE = 576
internal const val SAMPLES_LAYER1 = 384
internal const val SAMPLES_LAYER2_OR_L3_MPEG1 = 1152
internal const val MINIMP3_MAX_SAMPLES_PER_FRAME = SAMPLES_LAYER2_OR_L3_MPEG1 * 2
internal const val MAX_FREE_FORMAT_FRAME_SIZE = 2304
internal const val MAX_FRAME_SYNC_MATCHES = 10
internal const val MAX_L3_FRAME_PAYLOAD_BYTES = MAX_FREE_FORMAT_FRAME_SIZE
internal const val MAX_BITRESERVOIR_BYTES = 511
internal const val SHORT_BLOCK_TYPE = 2
internal const val STOP_BLOCK_TYPE = 3
internal const val MODE_MONO = 3
internal const val MODE_JOINT_STEREO = 1
internal const val HDR_SIZE = 4
internal const val BITS_DEQUANTIZER_OUT = -1
internal const val MAX_SCF = 255 + BITS_DEQUANTIZER_OUT * 4 - 210
internal const val MAX_SCFI = (MAX_SCF + 3) and 3.inv()

/** Bytes-per-second factor: kbps * 125 == bitrate_bps / 8. */
internal const val FRAME_BYTES_KBPS_FACTOR = 125

internal fun miniMp3Min(a: Int, b: Int): Int = if (a > b) b else a
internal fun miniMp3Max(a: Int, b: Int): Int = if (a < b) b else a

/**
 * Zero-copy view of a 4-byte MPEG frame header (matches minimp3 HDR_* macros).
 * Holds a reference into the bitstream buffer — do not mutate [bytes] while in use.
 */
internal class Mp3Hdr private constructor(
    private val bytes: ByteArray,
    private val offset: Int,
) {
    init {
        require(offset >= 0 && offset + HDR_SIZE <= bytes.size) {
            "MPEG header out of bounds: offset=$offset size=${bytes.size}"
        }
    }

    private fun u(i: Int): Int = bytes[offset + i].toInt() and 0xFF

    fun isMono(): Boolean = (u(3) and 0xC0) == 0xC0
    fun isCrc(): Boolean = (u(1) and 1) == 0
    fun testPadding(): Boolean = (u(2) and 0x2) != 0
    fun testMpeg1(): Boolean = (u(1) and 0x8) != 0
    fun testNotMpeg25(): Boolean = (u(1) and 0x10) != 0
    fun layerBits(): Int = (u(1) ushr 1) and 3
    fun bitrateIndex(): Int = u(2) ushr 4
    fun sampleRateIndex(): Int = (u(2) ushr 2) and 3
    fun mySampleRate(): Int =
        sampleRateIndex() + (((u(1) ushr 3) and 1) + ((u(1) ushr 4) and 1)) * 3
    fun isFrame576(): Boolean = (u(1) and 14) == 2
    fun isLayer1(): Boolean = (u(1) and 6) == 6

    companion object {
        fun from(src: ByteArray, offset: Int): Mp3Hdr = Mp3Hdr(src, offset)
    }
}

/**
 * Bit reader over a [ByteArray] slice. Indexed byte access (not kotlinx-io Source)
 * because Layer III Huffman / reservoir code needs random access and bit-granular reads.
 */
internal class BitStream {
    var buf: ByteArray = ByteArray(0)
    var bufOff: Int = 0
    var pos: Int = 0
    var limit: Int = 0

    fun init(data: ByteArray, offset: Int, bytes: Int) {
        require(offset >= 0 && bytes >= 0 && offset + bytes <= data.size) {
            "bitstream bounds: off=$offset bytes=$bytes size=${data.size}"
        }
        buf = data
        bufOff = offset
        pos = 0
        limit = bytes * 8
    }

    fun getBits(n: Int): Int {
        val s = pos and 7
        var shl = n + s
        var p = bufOff + (pos ushr 3)
        pos += n
        if (pos > limit) return 0
        var next = (buf[p].toInt() and 0xFF) and (255 ushr s)
        p++
        var cache = 0
        while (true) {
            shl -= 8
            if (shl <= 0) break
            cache = cache or (next shl shl)
            next = if (p < buf.size) buf[p].toInt() and 0xFF else 0
            p++
        }
        val right = -shl
        return cache or (if (right >= 0) next ushr right else next shl -right)
    }
}

internal class L3GrInfo {
    var sfbtab: IntArray = IntArray(0)
    var sfbtabOff: Int = 0
    var part23Length: Int = 0
    var bigValues: Int = 0
    var scalefacCompress: Int = 0
    var globalGain: Int = 0
    var blockType: Int = 0
    var mixedBlockFlag: Int = 0
    var nLongSfb: Int = 0
    var nShortSfb: Int = 0
    val tableSelect = IntArray(3)
    val regionCount = IntArray(3)
    val subblockGain = IntArray(3)
    var preflag: Int = 0
    var scalefacScale: Int = 0
    var count1Table: Int = 0
    var scfsi: Int = 0

    fun sfb(i: Int): Int = sfbtab[sfbtabOff + i]
}

/**
 * Per-decoder scratch: reused across frames to keep the hot path allocation-free
 * after [Mp3dec] construction.
 */
internal class Mp3decScratch {
    val bs = BitStream()
    val maindata = ByteArray(MAX_BITRESERVOIR_BYTES + MAX_L3_FRAME_PAYLOAD_BYTES)
    val grInfo = Array(4) { L3GrInfo() }
    /** Contiguous [ch0 | ch1] so midside can use left+576. */
    val grbuf = FloatArray(2 * SAMPLES_PER_GRANULE)
    val scf = FloatArray(40)
    /** Contiguous syn[(18+15)][64] — also QMF delay line workspace. */
    val syn = FloatArray((18 + 15) * 64)
    val istPos = Array(2) { IntArray(39) }

    // Hot-path workspaces (formerly stack/local allocations in Layer3 / synth)
    val scfSize = IntArray(4)
    val iscf = IntArray(40)
    val scfCounts = IntArray(28)
    val maxBand = IntArray(3)
    val dctT = FloatArray(4 * 8)
    val synthA = FloatArray(4)
    val synthB = FloatArray(4)
    val imdctCo = FloatArray(9)
    val imdctSi = FloatArray(9)
    val imdct12Co = FloatArray(3)
    val imdct12Si = FloatArray(3)
    val imdctShortTmp = FloatArray(18)
}

internal data class Mp3decFrameInfo(
    var frameBytes: Int = 0,
    var frameOffset: Int = 0,
    var channels: Int = 0,
    var hz: Int = 0,
    var layer: Int = 0,
    var bitrateKbps: Int = 0,
)

internal class Mp3dec {
    val mdctOverlap = Array(2) { FloatArray(9 * 32) }
    val qmfState = FloatArray(15 * 2 * 32)
    var reserv: Int = 0
    var freeFormatBytes: Int = 0
    val header = ByteArray(HDR_SIZE)
    val reservBuf = ByteArray(MAX_BITRESERVOIR_BYTES)
    /** Reused every [MiniMp3.decodeFrame] call. */
    val scratch = Mp3decScratch()
    val bsFrame = BitStream()
    /** Single-element holders for [MiniMp3Header.findFrame] out-params. */
    val freeFormatScratch = IntArray(1)
    val frameBytesScratch = IntArray(1)

    fun clear() {
        for (ch in 0 until 2) mdctOverlap[ch].fill(0f)
        qmfState.fill(0f)
        reserv = 0
        freeFormatBytes = 0
        header.fill(0)
        reservBuf.fill(0)
    }
}
