package space.kodio.core

import kotlinx.cinterop.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.AudioToolbox.*
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.darwin.OSStatus
import platform.darwin.UInt32Var
import platform.darwin.noErr
import platform.posix.memcpy
import space.kodio.core.util.namedLogger
import kotlin.concurrent.Volatile
import kotlin.math.max

private val logger = namedLogger("AudioQueue")

/**
 * Apple documents that enqueue is forbidden while [AudioQueueReset], [AudioQueueStop],
 * or [AudioQueueDispose] is in progress ([kAudioQueueErr_EnqueueDuringReset]).
 *
 * @see <a href="https://developer.apple.com/documentation/audiotoolbox/kaudioqueueerr_enqueueduringreset">kAudioQueueErr_EnqueueDuringReset</a>
 */
private const val AUDIO_QUEUE_ERR_ENQUEUE_DURING_RESET: OSStatus = -66632

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

        /**
         * Callback-visible state. [running] mirrors Apple's `mIsRunning` flag from the
         * Audio Queue Services Programming Guide (Recording Audio, Listing 2-5 / 2-14):
         * the input callback must return without enqueueing when the flag is clear.
         */
        class State(
            @Volatile var running: Boolean,
            val channel: Channel<ByteArray>
        )

        suspend fun streamTo(dst: SendChannel<ByteArray>) {
            val state = stateRef.get()
            state.channel.consumeEach { audioData ->
                dst.send(audioData)
            }
        }

        override fun markNotRunning() {
            val state = stateRef.get()
            state.running = false
            state.channel.close()
        }
    }

    class Writable(
        aqRef: AudioQueueRef,
        asbd: AudioStreamBasicDescription,
        stateRef: StableRef<State>
    ) : MacosAudioQueue<Writable.State>(aqRef, asbd, stateRef) {

        /**
         * Callback-visible state.
         *
         * [running] is cleared only on stop/dispose ([markNotRunning] closes the pool).
         * [enqueueEnabled] is the seek-quiesce flag: during seek, callbacks still return
         * buffers to the open pool but must not refill/enqueue. Seek must NOT clear
         * [running] or close [availableBuffers] (that starves re-prime after Stop).
         */
        class State(
            @Volatile var running: Boolean,
            @Volatile var enqueueEnabled: Boolean = true,
            val availableBuffers: Channel<AudioQueueBufferRef>
        )

        /**
         * Seek discard path (plan Phase 2): quiesce enqueue → [AudioQueueStop]`(true)`
         * → re-enable. Does **not** call [markNotRunning] / close the buffer pool.
         * Prefer this over [AudioQueueFlush] for seek (Flush is EOS decoder drain).
         */
        fun seekDiscardImmediate() {
            val state = stateRef.get()
            state.enqueueEnabled = false
            runAndCheckOsStatus { AudioQueueStop(aqRef, true) }
            // After synchronous Stop, flushed buffers have been returned via callbacks.
            state.enqueueEnabled = true
            logger.debug { "Writable.seekDiscardImmediate: quiesce+Stop(true)+re-enable" }
        }

        /**
         * Fill one available buffer from [src] and enqueue. Soft-fails DuringReset.
         * Returns bytes written into the buffer (`0` if no buffer / empty src / disabled).
         */
        fun tryPrimeBuffer(src: ByteArray, offset: Int, length: Int): Int {
            if (length <= 0) return 0
            val state = stateRef.get()
            if (!state.running || !state.enqueueEnabled) return 0
            val result = state.availableBuffers.tryReceive()
            val bufferRef = result.getOrNull() ?: return 0
            val buffer = bufferRef.pointed
            val written = buffer.write(src, offset, length)
            if (written <= 0) {
                state.availableBuffers.trySend(bufferRef)
                return 0
            }
            if (!aqRef.tryEnqueueBuffer(bufferRef)) {
                // Soft-fail DuringReset: return buffer to pool.
                state.availableBuffers.trySend(bufferRef)
                return 0
            }
            return written
        }

        /**
         * Block until [length] bytes from [src] are enqueued (or the queue stops).
         *
         * Transport [AudioSink.write] must not return early while the soft cursor
         * still advances: a non-blocking prime that only fills the free pool
         * (~devicePrimeDuration) causes the feed loop to race through the whole
         * PCM and leave only the first note audible.
         */
        fun enqueueBlocking(src: ByteArray, offset: Int, length: Int): Int {
            if (length <= 0) return 0
            var written = 0
            val state = stateRef.get()
            while (written < length && state.running) {
                if (!state.enqueueEnabled) {
                    // Seek quiesce: wait for Stop to return buffers / re-enable.
                    runBlocking { delay(1) }
                    continue
                }
                val bufferRef = runBlocking {
                    state.availableBuffers.receiveCatching().getOrNull()
                } ?: return written
                val buffer = bufferRef.pointed
                val n = buffer.write(src, offset + written, length - written)
                if (n <= 0) {
                    state.availableBuffers.trySend(bufferRef)
                    return written
                }
                if (!aqRef.tryEnqueueBuffer(bufferRef)) {
                    state.availableBuffers.trySend(bufferRef)
                    runBlocking { delay(1) }
                    continue
                }
                written += n
            }
            return written
        }

        suspend fun streamFrom(src: AudioFlow, maxWriteAttempts: Int = 10) {
            var chunkCount = 0
            var totalBytes = 0L
            logger.debug { "Writable.streamFrom started, format: ${src.format}" }
            src.collect { chunk ->
                chunkCount++
                totalBytes += chunk.size
                val state = stateRef.get()
                var writtenBytes = 0
                var attemptsLeft = maxWriteAttempts
                while (writtenBytes < chunk.size && --attemptsLeft > 0) {
                    logger.trace { "Waiting for available buffer (chunk #$chunkCount, ${chunk.size} bytes)..." }
                    val bufferRef = state.availableBuffers.receive()
                    logger.trace { "Got buffer, writing chunk..." }
                    val buffer = bufferRef.pointed
                    writtenBytes = buffer.write(chunk, 0, chunk.size)
                    logger.trace { "Wrote $writtenBytes bytes, enqueueing buffer..." }
                    aqRef.enqueueBuffer(bufferRef)
                }
                if (chunkCount % 50 == 0) {
                    logger.trace { "Progress: $chunkCount chunks, $totalBytes total bytes" }
                }
            }
            logger.debug { "Writable.streamFrom completed: $chunkCount chunks, $totalBytes total bytes" }
        }
        
        /**
         * Streams audio with proper priming - fills initial buffers before starting the queue.
         * This is required for output queues to work correctly.
         */
        suspend fun streamFromWithPriming(src: AudioFlow, primingBuffers: Int) {
            var chunkCount = 0
            var totalBytes = 0L
            var queueStarted = false
            var buffersEnqueued = 0
            
            logger.debug { "Writable.streamFromWithPriming started, format: ${src.format}, primingBuffers=$primingBuffers" }
            
            src.collect { chunk ->
                chunkCount++
                totalBytes += chunk.size
                val state = stateRef.get()
                var writtenBytes = 0
                
                while (writtenBytes < chunk.size) {
                    val bufferRef = state.availableBuffers.receive()
                    val buffer = bufferRef.pointed
                    val bytesWritten = buffer.write(chunk, writtenBytes, chunk.size - writtenBytes)
                    writtenBytes += bytesWritten
                    aqRef.enqueueBuffer(bufferRef)
                    buffersEnqueued++
                    
                    // Start the queue after priming buffers are filled
                    if (!queueStarted && buffersEnqueued >= primingBuffers) {
                        logger.debug { "Primed $buffersEnqueued buffers, starting queue..." }
                        runAndCheckOsStatus { AudioQueueStart(aqRef, null) }
                        queueStarted = true
                        logger.debug { "Queue started!" }
                    }
                }
            }
            
            // If we didn't enqueue enough to trigger start, start anyway
            if (!queueStarted && buffersEnqueued > 0) {
                logger.debug { "Starting queue with $buffersEnqueued buffers (less than priming target)" }
                runAndCheckOsStatus { AudioQueueStart(aqRef, null) }
                queueStarted = true
            }
            
            // Wait for all buffers to be played
            if (queueStarted) {
                logger.debug { "Waiting for playback to complete..." }
                // Flush ensures all enqueued buffers are played
                runAndCheckOsStatus { AudioQueueFlush(aqRef) }
                // Stop with inImmediate=false to let remaining buffers play
                runAndCheckOsStatus { AudioQueueStop(aqRef, false) }
            }
            
            logger.debug { "Writable.streamFromWithPriming completed: $chunkCount chunks, $totalBytes bytes, $buffersEnqueued buffers" }
        }

        override fun markNotRunning() {
            val state = stateRef.get()
            state.running = false
            state.availableBuffers.close()
        }
    }

    /**
     * Clears the callback-visible running flag (Apple's `mIsRunning`) and closes
     * channels so producers/consumers unwind. Must be called before stop/dispose
     * so [AudioQueueInputCallback] does not enqueue during reset.
     */
    protected abstract fun markNotRunning()

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
     * Per Apple Audio Queue Services Reference: [AudioQueueStop] also resets the
     * queue. With [inImmediate] `true`, stopping is synchronous; with `false`,
     * callbacks may continue until queued buffers finish.
     *
     * For immediate stop we clear the running flag first so in-flight callbacks
     * skip enqueue (see Recording Audio Listing 2-5 / 2-14).
     *
     * @param inImmediate If you pass `true`, stopping occurs immediately (that is, synchronously).
     *                    If you pass `false`, the function returns immediately,
     *                    but the audio queue does not stop until its queued buffers are played or recorded
     *                    (that is, the stop occurs asynchronously).
     *                    Audio queue callbacks are invoked as necessary until the queue actually stops.
     */
    fun stop(inImmediate: Boolean = true) {
        if (inImmediate) {
            markNotRunning()
        }
        runAndCheckOsStatus { AudioQueueStop(aqRef, inImmediate) }
        if (!inImmediate) {
            markNotRunning()
        }
    }

    /**
     * Resets the [aqRef].
     */
    fun reset(): Unit =
        runAndCheckOsStatus { AudioQueueReset(aqRef) }

    /**
     * Stops and disposes the [aqRef], matching Apple's recording cleanup sequence:
     * clear running → [AudioQueueStop]`(true)` → [AudioQueueDispose]`(true)`.
     *
     * Disposing of an audio queue also disposes of its resources, including its buffers.
     * After you call this function, you can no longer interact with the audio queue.
     * In addition, the audio queue no longer invokes any callbacks.
     *
     * @param inImmediate If you pass `true`, the audio queue is disposed of immediately (that is, synchronously).
     *                    If you pass false, disposal does not take place until all enqueued buffers
     *                    are processed (that is, asynchronously).
     *
     * @see <a href="https://developer.apple.com/library/archive/documentation/MusicAudio/Conceptual/AudioQueueProgrammingGuide/AQRecord/RecordingAudio.html">Recording Audio</a>
     */
    fun dispose(inImmediate: Boolean = true) {
        // 1) Stop accepting callback work before touching the queue (Apple mIsRunning).
        markNotRunning()
        // 2) Synchronous stop+reset so hardware/mic is released promptly.
        runCatching {
            runAndCheckOsStatus { AudioQueueStop(aqRef, true) }
        }.onFailure { logger.warn(it) { "AudioQueueStop during dispose failed" } }
        // 3) Dispose queue + buffers.
        runCatching {
            runAndCheckOsStatus { AudioQueueDispose(aqRef, inImmediate) }
        }.onFailure { logger.warn(it) { "AudioQueueDispose failed" } }
        runCatching { stateRef.dispose() }
            .onFailure { logger.warn(it) { "StableRef.dispose failed" } }
        runCatching { nativeHeap.free(asbd) }
            .onFailure { logger.warn(it) { "ASBD free failed" } }
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

            Apple Recording Audio Listing 2-5: check mIsRunning before enqueue;
            never throw across this AudioToolbox callback boundary (K/N aborts).
             */
            val inputCallBack: AudioQueueInputCallback =
                staticCFunction { inUserData,
                                  inAQ,
                                  inBuffer,
                                  inStartTime,
                                  inNumberPacketDescriptions,
                                  inPacketDescriptions ->
                    try {
                        if (inAQ == null || inUserData == null || inBuffer == null) {
                            logAb("Input callback called but inAQ/userData/buffer null")
                            return@staticCFunction
                        }
                        val state = inUserData.asStableRef<ReadOnly.State>().get()
                        // Listing 2-5: if (mIsRunning == 0) return; before enqueue
                        if (!state.running) {
                            logAb("Input callback after stop; skipping enqueue")
                            return@staticCFunction
                        }
                        val audioData = inBuffer.pointed.readFully()
                        if (audioData.isEmpty()) {
                            logAb("Input callback called but audioData is empty, re-enqueuing")
                            if (state.running) {
                                inAQ.tryEnqueueBuffer(inBuffer)
                            }
                        } else {
                            val result = state.channel.trySend(audioData)
                                .onFailure { logger.warn { "Input callback trySend failed: $it" } }
                            // Re-check running: stop/dispose may have raced after trySend.
                            if (state.running && !result.isClosed) {
                                inAQ.tryEnqueueBuffer(inBuffer)
                            }
                        }
                    } catch (t: Throwable) {
                        // Uncaught Kotlin exceptions in K/N AudioToolbox callbacks abort the process.
                        logAb("Input callback swallowed exception: ${t.message}")
                    }
                }

            // Unlimited: input callbacks must not drop PCM (trySend failure → audible distortion).
            val stateRef = StableRef.create(ReadOnly.State(true, Channel(Channel.UNLIMITED)))
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
            logger.info {
                "createOutput format=$format rate=${format.sampleRate} ch=${format.channels.count} " +
                    "bpf=${format.bytesPerFrame} buffers=$bufferCount durationSec=$bufferDurationSec"
            }
            val outputCallBack: AudioQueueOutputCallback =
                staticCFunction { inUserData,
                                  inAQ,
                                  inBuffer ->
                    try {
                        if (inUserData == null || inBuffer == null) {
                            logAb("Output callback called but userData/buffer null")
                            return@staticCFunction
                        }
                        val state = inUserData.asStableRef<Writable.State>().get()
                        if (!state.running) {
                            // stop/dispose: drop everything (pool may be closed).
                            logAb("Output callback after stop/dispose; dropping buffer")
                            return@staticCFunction
                        }
                        // Seek-quiesce and normal play: always return buffer to the open pool.
                        logAb("[AudioQueue] Output callback: buffer played, returning to pool")
                        state.availableBuffers
                            .trySend(inBuffer)
                            .onFailure { logAb("Output callback failed to send buffer: $it") }
                        // Do not refill here — transport feed / streamFrom owns enqueue.
                    } catch (t: Throwable) {
                        logAb("Output callback swallowed exception: ${t.message}")
                    }
                }
            val state = Writable.State(
                running = true,
                enqueueEnabled = true,
                availableBuffers = Channel(bufferCount),
            )
            val stateRef = StableRef.create(state)
            val aqRefVar: AudioQueueRefVar = nativeHeap.alloc()
            val asbd: AudioStreamBasicDescription = nativeHeap.allocASBD(format)
            logger.debug {
                "ASBD rate=${asbd.mSampleRate} formatID=${asbd.mFormatID} flags=0x${asbd.mFormatFlags.toString(16)} " +
                    "bits=${asbd.mBitsPerChannel} ch=${asbd.mChannelsPerFrame} " +
                    "bpf=${asbd.mBytesPerFrame} bpp=${asbd.mBytesPerPacket} fpp=${asbd.mFramesPerPacket}"
            }
            logger.debug { "Creating AudioQueueNewOutput..." }
            return runCatching {
                runAndCheckOsStatus {
                    AudioQueueNewOutput(
                        inFormat = asbd.ptr,
                        inCallbackProc = outputCallBack,
                        inUserData = stateRef.asCPointer(),
                        inCallbackRunLoop = null,            // <— let AQ manage its own thread
                        inCallbackRunLoopMode = null,        // <— no mode needed
                        inFlags = 0u,
                        outAQ = aqRefVar.ptr
                    )
                }
                logger.debug { "AudioQueueNewOutput created successfully" }
                aqRefVar.value ?: error("Failed to create audio queue")
            }.mapCatching { aqRef ->
                logger.debug { "Allocating $bufferCount buffers (output - no enqueue)..." }
                val buffers = aqRef
                    .allocBuffersOnly(
                        sampleRate = asbd.mSampleRate.toInt(),
                        bytesPerFrame = asbd.mBytesPerFrame.toInt(),
                        bufferCount = bufferCount,
                        bufferDurationSec = bufferDurationSec
                    )
                logger.debug { "Allocated ${buffers.size} buffers, adding to available pool..." }
                buffers.forEach {
                    val bufferRef = it.value ?: error("Failed to allocate audio buffer")
                    state.availableBuffers.trySend(bufferRef)
                }
                logger.debug { "createOutput completed successfully" }
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

/**
 * Allocates and enqueues buffers for INPUT queues.
 * For input queues, empty buffers are enqueued for the system to fill with audio data.
 */
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
 * Allocates buffers for OUTPUT queues (without enqueueing).
 * For output queues, buffers should only be enqueued after writing audio data to them.
 */
@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueRef.allocBuffersOnly(
    sampleRate: Int,
    bytesPerFrame: Int,
    bufferCount: Int,
    bufferDurationSec: Double,
): List<AudioQueueBufferRefVar> = List(bufferCount) { idx ->
    val bufferRefVar: AudioQueueBufferRefVar = nativeHeap.alloc()
    runCatching {
        allocBuffer(sampleRate, bytesPerFrame, bufferRefVar, bufferDurationSec)
        // Don't enqueue - output buffers should only be enqueued after writing data
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

/**
 * Callback-safe enqueue: soft-fails [AUDIO_QUEUE_ERR_ENQUEUE_DURING_RESET] instead of throwing.
 * Apple forbids enqueue during Reset/Stop/Dispose; racing InputBufferComplete callbacks hit this.
 */
@OptIn(ExperimentalForeignApi::class)
private fun AudioQueueRef.tryEnqueueBuffer(bufferRef: AudioQueueBufferRef): Boolean {
    val status = AudioQueueEnqueueBuffer(
        inAQ = this@tryEnqueueBuffer,
        inBuffer = bufferRef,
        inNumPacketDescs = 0u,
        inPacketDescs = null
    )
    if (status.toUInt() == noErr) return true
    if (status == AUDIO_QUEUE_ERR_ENQUEUE_DURING_RESET) {
        logAb("enqueue skipped during reset/stop/dispose (OSStatus=$status)")
        return false
    }
    // Unexpected errors: log only — never throw from the AQ callback path.
    logAb("enqueue failed with OSStatus=$status (soft-fail in callback)")
    return false
}

// Helpers

private fun logAb(message: String) = logger.trace { message }
