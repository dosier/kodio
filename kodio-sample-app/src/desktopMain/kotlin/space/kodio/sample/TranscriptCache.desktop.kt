package space.kodio.sample

import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration

actual fun createTranscriptCache(sessionLabel: String?): TranscriptCache? {
    return try {
        val homeDir = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return null
        val baseDir = File(homeDir, ".kodio/transcripts")
        if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.exists()) {
            return null
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sanitizedLabel = sessionLabel
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
        val name = if (sanitizedLabel != null) {
            "transcript-$timestamp-$sanitizedLabel.txt"
        } else {
            "transcript-$timestamp.txt"
        }
        val file = File(baseDir, name)
        if (!file.exists()) file.createNewFile()
        DesktopTranscriptCache(file)
    } catch (_: Throwable) {
        null
    }
}

private class DesktopTranscriptCache(private val file: File) : TranscriptCache {

    override val displayPath: String = file.absolutePath

    override fun appendFinal(start: Duration, end: Duration, text: String) {
        try {
            file.appendText("[${formatHms(start)} → ${formatHms(end)}] $text\n")
        } catch (_: Throwable) {
        }
    }

    override fun revealInFileExplorer(): Boolean = try {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        val parent = file.parentFile ?: return false
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(parent)
            true
        } else {
            false
        }
    } catch (_: Throwable) {
        false
    }
}

private fun formatHms(d: Duration): String {
    val totalSeconds = d.inWholeSeconds
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
