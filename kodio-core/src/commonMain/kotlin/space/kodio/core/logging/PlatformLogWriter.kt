package space.kodio.core.logging

/**
 * Returns a platform-appropriate default [KodioLogWriter] (stdout/Logcat/NSLog/console).
 *
 * Opt-in: register via [KodioLogging.addWriter] or [KodioLogging.setWriters]; not enabled automatically.
 */
expect fun platformLogWriter(): KodioLogWriter

internal fun formatLogLine(level: LogLevel, tag: String, message: String): String =
    "[$level] $tag: $message"
