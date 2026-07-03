package space.kodio.benchmark

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import space.kodio.core.DefaultRecordingInt16
import space.kodio.core.io.decode

@State(Scope.Benchmark)
class PcmCodecBenchmark {

    private var bytes: ByteArray? = null

    @Setup
    fun prepare() {
        bytes = create16BitData(List(10000) { it.toShort() })
    }

    @TearDown
    fun cleanup() {
        bytes = null
    }

    @Benchmark
    fun decodeInt16(): Array<BigDecimal> = decode(bytes!!, DefaultRecordingInt16)
}

// Helper: 16-bit LE from Short values
private fun create16BitData(samples: List<Short>): ByteArray {
    val result = ByteArray(samples.size * 2)
    var i = 0
    for (s in samples) {
        val v = s.toInt()
        result[i++] = (v and 0xFF).toByte()
        result[i++] = ((v ushr 8) and 0xFF).toByte()
    }
    return result
}
