package space.kodio.core.io

import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import space.kodio.core.AudioFlow
import space.kodio.core.AudioFormat

fun audioFlowOf(format: AudioFormat, vararg bytes: Byte) = AudioFlow(format, flow {
    emit(bytes)
})

suspend fun AudioFlow.collectAsSource(): AudioSource  {
    val buffer = Buffer()
    collect {
        buffer.write(it)
    }
    return AudioSource.of(format, buffer)
}