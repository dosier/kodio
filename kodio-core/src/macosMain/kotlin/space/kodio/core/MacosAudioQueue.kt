package space.kodio.core

import kotlinx.cinterop.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.onFailure
import platform.AudioToolbox.*
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.kCFRunLoopCommonModes
import platform.darwin.UInt32Var
import platform.posix.memcpy
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
sealed class MacosAudioQueue<S : Any>(
    internal val aqRef: AudioQueueRef,
    internal val asbd: AudioStreamBasicDescription,
    internal val stateRef: StableRef<S>
) {

    class ReadOnly(
        aqRef: AudioQueueRef,
        asbd: AudioStreamBasicDescription,
        stateRef: StableRef<State>
    ) : MacosAudioQueue<ReadOnly.State>(aqRef, asbd, stateRef) {

        class State(
            val running: Boolean,
            val channel: Channel<ByteArray>
        )

        suspend fun streamTo(dst: SendChannel<ByteArray>) {
            val state = stateRef.get()
            state.channel.consumeEach { audioData ->
//                println("received audio data: size = ${audioData.size} -> non-zero bytes = ${audioData.filter { it != 0.toByte() }.toByteArray().contentToString()}")
                dst.send(audioData)
            }
        }
    }

    class Writable(
        aqRef: AudioQueueRef,
        asbd: AudioStreamBasicDescription,
        stateRef: StableRef<State>
    ) : MacosAudioQueue<Writable.State>(aqRef, asbd, stateRef) {

        class State(
            val running: Boolean,
            val availableBuffers: Channel<AudioQueueBufferRef>
        )

        suspend fun streamFrom(src: AudioFlow, maxWriteAttempts: Int = 10) {
            src.collect { chunk ->
                val state = stateRef.get()
                var writtenBytes = 0
                var attemptsLeft = maxWriteAttempts
                while (writtenBytes < chunk.size && --attemptsLeft > 0) {
                    val bufferRef = state.availableBuffers.receive()
                    val buffer = bufferRef.pointed
                    writtenBytes = buffer.write(chunk, 0, chunk.size)
                    aqRef.enqueueBuffer(bufferRef)
                }
            }
        }
    }

    /**
     * Begins playing or recordings audio.
     */
    fun start(): Unit =
        runAndCheckOsStatus { AudioQueueStart(aqRef, null) }

    /**
     * Pauses playing or recording audio.
     */
    fun pause(): Unit =
        runAndCheckOsStatus { AudioQueuePause(aqRef) }

    /**
     * Resets the [aqRef]'s decoder state.
     */
    fun flush(): Unit =
        runAndCheckOsStatus { AudioQueueFlush(aqRef) }

    /**
     * Stops playing or recording audio.
     *
     * @param inImmediate If you pass `true`, stopping occurs immediately (that is, synchronously).
     *                    If you pass `false`, the function returns immediately,
     *                    but the audio queue does not stop until its queued buffers are played or recorded
     *                    (that is, the stop occurs asynchronously).
     *                    Audio queue callbacks are invoked as necessary until the queue actually stops.
     */
    fun stop(inImmediate: Boolean = true): Unit =
        runAndCheckOsStatus { AudioQueueStop(aqRef, inImmediate) }

    /**
     * Resets the [aqRef].
     */
    fun reset(): Unit =
        runAndCheckOsStatus { AudioQueueReset(aqRef) }

    /**
     * Disposes the [aqRef].
     *
     * Disposing of an audio queue also disposes of its resources, including its buffers.
     * After you call this function, you can no longer interact with the audio queue.
     * In addition, the audio queue no longer invokes any callbacks.
     *
     * @param inImmediate If you pass `true`, the audio queue is disposed of immediately (that is, synchronously).
     *                    If you pass false, disposal does not take place until all enqueued buffers
     *                    are processed (that is, asynchronously).
     */
    fun dispose(inImmediate: Boolean = true) {
        stateRef.dispose()
        nativeHeap.free(asbd)
        runAndCheckOsStatus { AudioQueueDispose(aqRef, inImmediate) }
    }

    internal inline fun <reified V : CVariable, reified T> getPropertyValue(property: MacosAudioQueueProperty<V, T>): T =
        memScoped {
            val propertySize: UInt32Var = alloc()
            runAndCheckOsStatus {
                AudioQueueGetPropertySize(
                    inAQ = aqRef,
                    inID = property.id,
                    outDataSize = propertySize.ptr
                )
            }
            val propertyVar = alloc<V>()
            runAndCheckOsStatus {
                AudioQueueGetProperty(
                    inAQ = aqRef,
                    inID = property.id,
                    outData = propertyVar.ptr,
                    ioDataSize = propertySize.ptr
                )
            }
            property.readValue(propertyVar)
        }

    internal inline fun <reified V : CVariable, T> setPropertyValue(property: MacosAudioQueueProperty<V, T>, value: T) =
        memScoped {
            runAndCheckOsStatus {
                AudioQueueSetProperty(
                    inAQ = aqRef,
                    inID = property.id,
                    inData = property.alloc(this, value).ptr,
                    inDataSize = sizeOf<V>().convert()
                )
            }
        }

//    internal inline fun <reified V : CVariable, reified T> addPropertyListener(
//        property: MacosAudioQueueProperty<V, T>,
//        crossinline onChange: (T) -> Unit
//    ): Unit = runAndCheckOsStatus {
//        AudioQueueAddPropertyListener(
//            inAQ = aqRef,
//            inID = property.id,
//            inProc = staticCFunction { inUserData, inAQ, inID ->
//                onChange(getPropertyValue(property))
//            },
//            inUserData = null
//        )
//    }

    companion object Companion {

        @OptIn(DelicateCoroutinesApi::class)
        fun createInput(
            format: AudioFormat,
            bufferCount: Int,
            bufferDurationSec: Double
        ): ReadOnly {
            /*
            The callback is invoked each time its recording audio queue
            has filled an audio queue buffer with fresh audio data.
            Typically, your callback writes the data to a file or other buffer
            and then re-enqueues the audio queue buffer to receive more data.
             */
            val inputCallBack: AudioQueueInputCallback =
                staticCFunction { inUserData,
                                  inAQ,
                                  inBuffer,
                                  inStartTime,
                                  inNumberPacketDescriptions,
                                  inPacketDescriptions ->
                    if (inAQ == null || inUserData == null || inBuffer == null)
                        logAb("Input callback called but inAQ/userData/buffer null")
                    else {
                        val state = inUserData.asStableRef<ReadOnly.State>().get()
                        if (!state.running)
                            logAb("Input callback called but state is not running")
                        else {
                            val audioData = inBuffer.pointed.readFully()
                            if (audioData.isEmpty())
                                logAb("Input callback called but audioData is empty, re-enqueuing")
                            else {
                                val result = state.channel.trySend(audioData)
                                    .onFailure { if (it != null) logAb("Input callback failed to send audio data: $it") }
                               if (!result.isClosed)
                                   inAQ.enqueueBuffer(inBuffer)
                            }
                        }
                    }
                }

            val stateRef = StableRef.create(ReadOnly.State(true, Channel()))
            val aqRefVar: AudioQueueRefVar = nativeHeap.alloc()
            val asbd: AudioStreamBasicDescription = nativeHeap.allocASBD(format)
            return runCatching {
                runAndCheckOsStatus {
                    AudioQueueNewInput(
                        inFormat = asbd.ptr,
                        inCallbackProc = inputCallBack,
                        inUserData = stateRef.asCPointer(),
                        inCallbackRunLoop = null,            // <— let AQ manage its own thread
                        inCallbackRunLoopMode = null,        // <— no mode needed
                        inFlags = 0u,
                        outAQ = aqRefVar.ptr
                    )
                }
                val aqRef = aqRefVar.value ?: error("Failed to create audio queue")
                aqRef.allocAndEnqueueBuffers(
                    sampleRate = asbd.mSampleRate.toInt(),
                    bytesPerFrame = asbd.mBytesPerFrame.toInt(),
                    bufferCount = bufferCount,
                    bufferDurationSec = bufferDurationSec
                )
                ReadOnly(aqRef, asbd, stateRef)
            }.onFailure {
                stateRef.dispose()
                nativeHeap.free(aqRefVar)
                nativeHeap.free(asbd)
            }.getOrThrow()
        }

        fun createOutput(
            format: AudioFormat,
            bufferCount: Int,
            bufferDurationSec: Double
        ): Writable {
            val outputCallBack: AudioQueueOutputCallback =
                staticCFunction { inUserData,
                                  inAQ,
                                  inBuffer ->
                    if (inUserData == null || inBuffer == null)
                        logAb("Output callback called but userData/buffer null")
                    else {
                        val state = inUserData.asStableRef<Writable.State>().get()
                        if (!state.running)
                            logAb("Output callback called but state is not running")
                        else
                            state.availableBuffers
                                .trySend(inBuffer)
                                .onFailure { logAb("Output callback failed to send buffer: $it") }
                    }
                }
            val state = Writable.State(running = true, availableBuffers = Channel(bufferCount))
            val stateRef = StableRef.create(state)
            val aqRefVar: AudioQueueRefVar = nativeHeap.alloc()
            val asbd: AudioStreamBasicDescription = nativeHeap.allocASBD(format)
            return runCatching {
                runAndCheckOsStatus {
                    AudioQueueNewOutput(
                        inFormat = asbd.ptr,
                        inCallbackProc = outputCallBack,
                        inUserData = stateRef.asCPointer(), // <-- pass user data here
                        inCallbackRunLoop = CFRunLoopGetCurrent(),
                        inCallbackRunLoopMode = kCFRunLoopCommonModes,
                        inFlags = 0u,
                        outAQ = aqRefVar.ptr
                    )
                }
                aqRefVar.value ?: error("Failed to create audio queue")
            }.mapCatching { aqRef ->
                aqRef
                    .allocAndEnqueueBuffers(
                        sampleRate = asbd.mSampleRate.toInt(),
                        bytesPerFrame = asbd.mBytesPerFrame.toInt(),
                        bufferCount = bufferCount,
                        bufferDurationSec = bufferDurationSec
                    )
                    .forEach {
                        val bufferRef = it.value ?: error("Failed to allocate audio buffer")
                        state.availableBuffers.trySend(bufferRef)
                    }
                Writable(aqRef, asbd, stateRef)
            }.onFailure {
                stateRef.dispose()
                nativeHeap.free(asbd)
                nativeHeap.free(aqRefVar)
            }.getOrThrow()
        }
    }
}

// AudioQueueBufferRef - extensions

@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueBuffer.readFully(): ByteArray =
    mAudioData?.readBytes(mAudioDataByteSize.toInt())
        ?: error("Failed to read audio data from buffer (mAudioData = null, mAudioDataByteSize = $mAudioDataByteSize)")

@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueBuffer.write(src: ByteArray, from: Int, length: Int): Int {
    val toWrite = minOf(length, mAudioDataBytesCapacity.toInt())
    memcpy(mAudioData, src.refTo(from), toWrite.convert())
    mAudioDataByteSize = toWrite.convert()
    return toWrite
}

// AudioQueueRef - extensions

@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueRef.allocAndEnqueueBuffers(
    sampleRate: Int,
    bytesPerFrame: Int,
    bufferCount: Int,
    bufferDurationSec: Double,
): List<AudioQueueBufferRefVar> = List(bufferCount) { idx ->
    val bufferRefVar: AudioQueueBufferRefVar = nativeHeap.alloc()
    runCatching {
        allocBuffer(sampleRate, bytesPerFrame, bufferRefVar, bufferDurationSec)
        enqueueBuffer(bufferRefVar.value ?: error("Failed to allocate audio buffer"))
    }.onFailure { error ->
        nativeHeap.free(bufferRefVar)
        throw error
    }
    bufferRefVar
}

/**
 * Allocates a buffer for the audio queue,
 * its memory is set free on disposal of [this] audio queue.
 */
@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueRef.allocBuffer(
    sampleRate: Int,
    bytesPerFrame: Int,
    bufferRefVar: AudioQueueBufferRefVar,
    bufferDurationSec: Double
) {
    val framesPerBuffer = max(1, (sampleRate * bufferDurationSec).toInt())
    val bytesPerBuffer = bytesPerFrame * framesPerBuffer
    runAndCheckOsStatus {
        AudioQueueAllocateBuffer(
            inAQ = this@allocBuffer,
            inBufferByteSize = bytesPerBuffer.toUInt(),
            outBuffer = bufferRefVar.ptr
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueRef.enqueueBuffer(bufferRef: AudioQueueBufferRef) {
    runAndCheckOsStatus {
        AudioQueueEnqueueBuffer(
            inAQ = this@enqueueBuffer,
            inBuffer = bufferRef,
            inNumPacketDescs = 0u,
            inPacketDescs = null
        )
    }
}

// Helpers

private fun logAb(message: String) = println("[AQ]: $message")