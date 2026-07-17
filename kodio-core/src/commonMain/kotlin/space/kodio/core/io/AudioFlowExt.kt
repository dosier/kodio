package space.kodio.core.io

import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat

/**
 * Creates a single-chunk [AudioFlow] from [bytes] interpreted as PCM in
 * [format].
 *
 * All vararg elements are concatenated into one [ByteArray] emission.
 */
fun audioFlowOf(format: AudioFormat, vararg bytes: Byte) = AudioFlow(format, flow {
    emit(bytes)
})

/**
 * Collects every chunk from this flow into one contiguous PCM buffer wrapped
 * as an [AudioSource] with this flow's [AudioFlow.format].
 */
suspend fun AudioFlow.collectAsSource(): AudioSource  {
    val buffer = Buffer()
    collect {
        buffer.write(it)
    }
    return AudioSource.of(format, buffer)
}

/**
 * Collects every chunk into a single [ByteArray].
 *
 * Prefer this for finite recordings that need random-access seeking. Unbounded
 * live streams will not complete.
 */
suspend fun AudioFlow.collectToByteArray(): ByteArray {
    var total = 0
    val chunks = ArrayList<ByteArray>()
    collect { chunk ->
        chunks.add(chunk)
        total += chunk.size
    }
    if (chunks.isEmpty()) return ByteArray(0)
    if (chunks.size == 1) return chunks[0]
    val out = ByteArray(total)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(out, destinationOffset = offset)
        offset += chunk.size
    }
    return out
}