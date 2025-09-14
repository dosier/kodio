package space.kodio.benchmark

import kotlinx.benchmark.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import space.kodio.core.AudioFlow
import space.kodio.core.DefaultRecordingFloat32
import space.kodio.core.DefaultRecordingInt16
import space.kodio.core.io.audioFlowOf
import space.kodio.core.io.convertAudio

@State(Scope.Benchmark)
class AudioFlowConversionBenchmark {

    private var inFlow: AudioFlow? = null

    @Setup
    fun prepare() {
        inFlow = audioFlowOf(
            format = DefaultRecordingInt16,
            bytes = create16BitData(List(10000) { it.toShort() })
        )
    }

    @TearDown
    fun cleanup() {
        inFlow = null
    }

    @Benchmark
    fun convertFlow() = runBlocking {
        inFlow!!
            .convertAudio(DefaultRecordingFloat32)
            .collect()
    }
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
