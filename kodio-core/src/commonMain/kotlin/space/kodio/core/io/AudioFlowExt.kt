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