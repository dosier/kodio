package space.kodio.core

import kotlinx.coroutines.flow.Flow

/**
 * A typed stream of raw PCM audio: an [AudioFormat] paired with a [Flow] of
 * [ByteArray] chunks.
 *
 * Every emitted chunk is PCM in [format]: same sample rate, channel count, and
 * sample encoding. Chunk boundaries are arbitrary; consumers must treat the
 * stream as one continuous byte sequence.
 *
 * The wrapped [Flow] may be cold or hot. A cold flow re-emits its full
 * sequence on each collector (typical for replayable recordings). A hot flow
 * shares live emissions among concurrent collectors (typical while recording).
 * [format] always describes the PCM layout of every chunk, regardless of
 * cold or hot behavior.
 *
 * ## Finite vs unbounded
 *
 * When [knownByteCount] is non-null, the stream is a known-finite recording of
 * exactly that many PCM bytes and is safe to materialize for buffer-backed seek.
 * When null (live monitors, infinite/long streams, unknown size), playback must
 * stream chunks without draining the whole flow; seek should return
 * [SeekResult.Unsupported].
 */
class AudioFlow(
    /** PCM layout shared by every chunk in the wrapped flow. */
    val format: AudioFormat,
    data: Flow<ByteArray>,
    /**
     * Total PCM byte length when this flow is a known-finite recording.
     * `null` means unbounded, live, or unknown — never fully collect for seek.
     */
    val knownByteCount: Long? = null,
) : Flow<ByteArray> by data {

    /** `true` when [knownByteCount] is set and buffer-backed seek is allowed. */
    val isKnownFinite: Boolean
        get() = knownByteCount != null
}
