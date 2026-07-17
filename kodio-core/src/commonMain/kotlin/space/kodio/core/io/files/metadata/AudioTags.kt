package space.kodio.core.io.files.metadata

/**
 * Tag metadata extracted from an audio file container.
 *
 * Parsers for individual formats are filled in Phase 2; until then most fields
 * remain null and [raw] is empty.
 */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val comment: String? = null,
    val raw: Map<String, String> = emptyMap(),
) {
    companion object {
        val Empty: AudioTags = AudioTags()
    }
}
