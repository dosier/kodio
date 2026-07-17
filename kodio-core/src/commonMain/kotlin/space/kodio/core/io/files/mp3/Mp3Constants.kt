package space.kodio.core.io.files.mp3

/** Shared MPEG audio frame / probe constants (cold path + facade). */
internal object Mp3Constants {
    const val FRAME_HEADER_BYTES = 4

    /** First sync byte of an MPEG frame header. */
    const val SYNC_BYTE = 0xFF

    /** Upper 3 bits of the second header byte must be set (11-bit sync). */
    const val SYNC_MASK = 0xE0

    /** How far [Mp3Codec.matchesMagic] scans a probe prefix for a frame header. */
    const val MAGIC_PROBE_SCAN_BYTES = 64

    /** Max bytes to scan when locating the first frame after ID3v2. */
    const val FIRST_FRAME_SCAN_LIMIT = 64 * 1024

    const val SAMPLES_LAYER1 = 384
    const val SAMPLES_LAYER2 = 1152
    const val SAMPLES_LAYER3_MPEG1 = 1152
    const val SAMPLES_LAYER3_MPEG2 = 576

    const val MPEG_VERSION_1 = 1
    const val MPEG_VERSION_2 = 2
    const val MPEG_VERSION_25 = 25

    const val CHANNEL_MODE_MONO = 3

    /** Xing TOC size in bytes. */
    const val XING_TOC_BYTES = 100

    /** VBRI tag fixed offset after the 4-byte frame header (Layer III). */
    const val VBRI_OFFSET_AFTER_HEADER = 32
}
