package space.kodio.sample

import kotlin.time.Duration

/**
 * A rolling, append-only transcript file scoped to a single transcription session.
 *
 * Implementations write each Final chunk to ONE file as it arrives, so the caller
 * always has the complete transcript-so-far on disk even if the app crashes mid-run.
 *
 * Returned by [createTranscriptCache]; null on platforms without filesystem access
 * (web). The caller should check for null and hide the "open folder" UI affordance
 * when the cache is unavailable.
 */
interface TranscriptCache {

    /** Absolute path on disk to the underlying file, for display in UI. */
    val displayPath: String

    /** Append a single Final result to the rolling file. Best-effort; failures are swallowed. */
    fun appendFinal(start: Duration, end: Duration, text: String)

    /**
     * Reveal the parent folder in the OS file explorer (Finder on macOS, Explorer
     * on Windows, etc.). Returns true on success.
     */
    fun revealInFileExplorer(): Boolean
}

/**
 * Create a new transcript cache scoped to the current session.
 *
 * @param sessionLabel optional label baked into the filename for easier scanning
 *   later (e.g. the source audio file name for FileUploadTab). When null, only
 *   the timestamp is used.
 *
 * Returns null on platforms without filesystem access.
 */
expect fun createTranscriptCache(sessionLabel: String? = null): TranscriptCache?
