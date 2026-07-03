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
 */
class AudioFlow(
    /** PCM layout shared by every chunk in the wrapped flow. */
    val format: AudioFormat,
    data: Flow<ByteArray>
) : Flow<ByteArray> by data