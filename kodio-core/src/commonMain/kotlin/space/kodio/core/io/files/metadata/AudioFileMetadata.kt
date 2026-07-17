package space.kodio.core.io.files.metadata

import kotlin.time.Duration
import space.kodio.core.Channels
import space.kodio.core.io.files.AudioFileFormat

/**
 * Header-level metadata for an audio file without decoding the full PCM payload.
 *
 * Tag parsing is implemented in Phase 2; Phase 1 returns technical fields with
 * [AudioTags.Empty] for PCM containers.
 */
data class AudioFileMetadata(
    val container: AudioFileFormat,
    val sampleRate: Int,
    val channels: Channels,
    val encoding: AudioEncodingInfo,
    val duration: Duration?,
    val tags: AudioTags,
)
