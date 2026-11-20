package space.kodio.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import space.kodio.core.io.convertAudio
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents a recorded audio clip with its format metadata.
 * 
 * This is a higher-level abstraction over [AudioFlow] that provides
 * convenient methods for common operations like saving to file and playback.
 * 
 * AudioRecording is immutable and thread-safe. All factory methods create
 * defensive copies of input data to prevent external mutation.
 * 
 * ## Example Usage
 * ```kotlin
 * val recording = Kodio.record(duration = 5.seconds)
 * 
 * // Save to file
 * recording.saveAs(Path("voice_note.wav"))
 * 
 * // Play it back
 * recording.play()
 * 
 * // Access raw data
 * val bytes = recording.toByteArray()
 * ```
 */
class AudioRecording private constructor(
    /** The audio format of this recording */
    val format: AudioFormat,
    /** Duration supplied by the creator (e.g. container metadata), if any. */
    private val providedDuration: Duration?,
    /** The underlying audio data as chunks (immutable after construction) */
    private val chunks: List<ByteArray>
) {
    /**
     * The duration of the recording: the one supplied when it was created, or
     * else computed from the PCM length. `null` only when the format cannot
     * express a duration (sample rate or frame size of 0).
     */
    val duration: Duration?
        get() = providedDuration ?: pcmLength

    /** Duration from frame count and sample rate; `null` if not computable. */
    private val pcmLength: Duration? by lazy {
        if (format.sampleRate <= 0 || format.bytesPerFrame <= 0) {
            null
        } else {
            // Safe calculation to avoid overflow: (frames / rate) * 1000 + remainder
            val seconds = frameCount / format.sampleRate
            val remainderFrames = frameCount % format.sampleRate
            val remainderMs = (remainderFrames * 1000L) / format.sampleRate
            (seconds * 1000L + remainderMs).milliseconds
        }
    }

    /**
     * The total size of the audio data in bytes.
     */
    val sizeInBytes: Long by lazy {
        chunks.sumOf { it.size.toLong() }
    }

    /**
     * The number of audio frames in this recording.
     * A frame contains one sample per channel.
     */
    val frameCount: Long by lazy {
        if (format.bytesPerFrame > 0) sizeInBytes / format.bytesPerFrame else 0L
    }

    /**
     * The calculated duration based on frame count and sample rate.
     * Falls back to the provided duration if available.
     * 
     * Uses safe arithmetic to avoid overflow for long recordings.
     */
    val calculatedDuration: Duration
        get() = duration ?: Duration.ZERO

    /**
     * Whether this recording is empty (no audio data).
     */
    val isEmpty: Boolean
        get() = chunks.isEmpty() || sizeInBytes == 0L

    /**
     * Whether this recording has audio data.
     */
    val hasData: Boolean
        get() = !isEmpty

    /**
     * Returns the audio data as a [Flow] of byte array chunks.
     * 
     * @param defensiveCopy If true (default), each emitted chunk is a copy.
     *                      Set to false for performance-critical streaming where
     *                      you guarantee not to mutate the received arrays.
     */
    fun asFlow(defensiveCopy: Boolean = true): Flow<ByteArray> = flow {
        if (defensiveCopy) {
            chunks.forEach { emit(it.copyOf()) }
        } else {
            chunks.forEach { emit(it) }
        }
    }

    /**
     * Returns the audio data as an [AudioFlow] for compatibility with existing APIs.
     *
     * Sets [AudioFlow.knownByteCount] so playback sessions may materialize once
     * for buffer-backed seek without treating live/unbounded flows the same way.
     */
    fun asAudioFlow(): AudioFlow =
        AudioFlow(format, asFlow(defensiveCopy = true), knownByteCount = sizeInBytes)

    /**
     * Collects all chunks into a single byte array.
     * Note: This loads all audio data into memory.
     * Returns a new array (not a reference to internal data).
     */
    fun toByteArray(): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        if (chunks.size == 1) return chunks[0].copyOf()
        
        val buffer = Buffer()
        chunks.forEach { buffer.write(it) }
        return buffer.readByteArray()
    }

    /**
     * Saves the recording to a file in the specified format.
     * 
     * @param path The file path to save to
     * @param fileFormat The audio file format (default: WAV)
     */
    suspend fun saveAs(path: Path, fileFormat: AudioFileFormat = AudioFileFormat.Wav) {
        asAudioFlow().writeToFile(fileFormat, path)
    }

    /**
     * Plays back this recording using the default audio output device.
     * This is a convenience method that creates a playback session internally.
     */
    suspend fun play() {
        Kodio.play(this)
    }

    override fun toString(): String {
        return "AudioRecording(format=$format, duration=$calculatedDuration, size=$sizeInBytes bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AudioRecording
        if (format != other.format) return false
        if (sizeInBytes != other.sizeInBytes) return false
        return toByteArray().contentEquals(other.toByteArray())
    }

    override fun hashCode(): Int {
        // Intentionally coarse: content is not hashed to keep hashCode cheap.
        var result = format.hashCode()
        result = 31 * result + sizeInBytes.hashCode()
        return result
    }

    companion object {
        /**
         * Creates an [AudioRecording] from an [AudioFlow] by collecting all chunks.
         * This suspends until the flow completes.
         * 
         * Note: The collected chunks are used directly since they come from the flow
         * and are not shared elsewhere.
         */
        suspend fun fromAudioFlow(audioFlow: AudioFlow, duration: Duration? = null): AudioRecording {
            val chunks = audioFlow.toList()
            return AudioRecording(
                format = audioFlow.format,
                providedDuration = duration,
                chunks = chunks
            )
        }

        /**
         * Creates an [AudioRecording] from raw PCM byte data (no container headers).
         * The data is copied to prevent external mutation.
         */
        fun fromPcmBytes(format: AudioFormat, data: ByteArray): AudioRecording {
            return AudioRecording(
                format = format,
                providedDuration = null,
                chunks = listOf(data.copyOf())
            )
        }

        /**
         * Creates an [AudioRecording] from multiple byte array chunks.
         * Each chunk is copied to prevent external mutation.
         */
        fun fromChunks(format: AudioFormat, chunks: List<ByteArray>, duration: Duration? = null): AudioRecording {
            return AudioRecording(
                format = format,
                providedDuration = duration,
                chunks = chunks.map { it.copyOf() }
            )
        }

        /**
         * Creates an empty recording placeholder.
         */
        fun empty(format: AudioFormat = AudioQuality.Default.format): AudioRecording {
            return AudioRecording(format = format, providedDuration = null, chunks = emptyList())
        }

        /**
         * Stitches multiple separately recorded segments into a single contiguous
         * [AudioRecording].
         *
         * Record successive segments with separate `start()`/`stop()` cycles
         * (calling `reset()` in between), then concatenate the results:
         *
         * ```kotlin
         * val first = recorder.use { it.start(); waitForUserPause(); it.stop(); it.getRecording()!! }
         * val second = recorder.use { it.start(); waitForUserStop(); it.stop(); it.getRecording()!! }
         * val full = AudioRecording.concat(first, second)
         * ```
         *
         * If the segments share a format, the bytes are concatenated as-is (cheap).
         * If they differ, every segment is converted to [targetFormat] (defaults to
         * the first segment's format) using [convertAudio] before being joined.
         *
         * @param recordings Two or more segments to stitch (order is preserved).
         * @param targetFormat The desired output format. Defaults to the first
         *   recording's format.
         * @throws IllegalArgumentException if [recordings] is empty.
         */
        suspend fun concat(
            recordings: List<AudioRecording>,
            targetFormat: AudioFormat? = null,
        ): AudioRecording {
            require(recordings.isNotEmpty()) { "concat() requires at least one recording" }
            if (recordings.size == 1 && (targetFormat == null || recordings[0].format == targetFormat)) {
                return recordings[0]
            }

            val outputFormat = targetFormat ?: recordings.first().format
            val allSameFormat = recordings.all { it.format == outputFormat }

            val combinedChunks: MutableList<ByteArray> = mutableListOf()
            var totalDurationMillis = 0L
            var allDurationsKnown = true

            for (recording in recordings) {
                val source = if (recording.format == outputFormat) {
                    recording.asAudioFlow()
                } else {
                    recording.asAudioFlow().convertAudio(outputFormat)
                }
                source.collect { chunk -> combinedChunks.add(chunk) }

                // Only explicitly supplied durations are summed; otherwise the
                // combined recording computes its exact length from the PCM.
                val d = recording.providedDuration
                if (d == null) {
                    allDurationsKnown = false
                } else {
                    totalDurationMillis += d.inWholeMilliseconds
                }
            }

            val combinedDuration = if (allSameFormat && allDurationsKnown) {
                totalDurationMillis.milliseconds
            } else {
                null
            }

            return AudioRecording(
                format = outputFormat,
                providedDuration = combinedDuration,
                chunks = combinedChunks,
            )
        }

        /** Vararg overload of [concat] for ergonomic call sites. */
        suspend fun concat(
            vararg recordings: AudioRecording,
            targetFormat: AudioFormat? = null,
        ): AudioRecording = concat(recordings.toList(), targetFormat)

        /**
         * Internal factory that takes ownership of chunks (no copy).
         * Only for internal use where we control the chunk lifecycle.
         */
        internal fun fromOwnedChunks(format: AudioFormat, chunks: List<ByteArray>, duration: Duration? = null): AudioRecording {
            return AudioRecording(
                format = format,
                providedDuration = duration,
                chunks = chunks
            )
        }
    }
}
