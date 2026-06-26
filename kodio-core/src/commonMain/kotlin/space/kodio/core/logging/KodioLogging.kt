package space.kodio.core.logging

import kotlin.concurrent.Volatile

/**
 * Severity levels for Kodio logging.
 *
 * Ordinal order defines filtering: a log event is emitted when its level is at or above
 * [KodioLogging.minLevel]. [None] means no events are emitted regardless of writers.
 */
enum class LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    /** Disables all logging when set as [KodioLogging.minLevel]. */
    None,
}

/**
 * Receives log events routed by [KodioLogging].
 *
 * Implement this interface to bridge Kodio logs to your preferred backend
 * (Kermit, SLF4J, Logcat, os_log, browser console, etc.).
 *
 * ```
 * KodioLogging.configure {
 *     minLevel = LogLevel.Debug
 *     addWriter { level, tag, message, throwable ->
 *         MyAppLogger.log(level, tag, message, throwable)
 *     }
 * }
 * ```
 */
fun interface KodioLogWriter {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/** A [KodioLogWriter] that discards all events. Used as the implicit default. */
object NoOpLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

/**
 * Central configuration and dispatch for Kodio's multiplatform logging facade.
 *
 * **Silent by default.** No log output is produced until you configure a minimum level and
 * at least one [KodioLogWriter].
 *
 * ## Enable logging
 * ```
 * KodioLogging.configure {
 *     minLevel = LogLevel.Debug
 *     addWriter(platformLogWriter())
 * }
 * ```
 *
 * Or from the library entry point:
 * ```
 * Kodio.configureLogging {
 *     minLevel = LogLevel.Info
 *     addWriter(platformLogWriter())
 * }
 * ```
 *
 * ## Disable logging
 * ```
 * KodioLogging.minLevel = LogLevel.None
 * // or
 * KodioLogging.clearWriters()
 * ```
 *
 * ## Custom writers
 * Add any number of [KodioLogWriter] instances; each log event is forwarded to every writer.
 * Replace the full set with [setWriters], append with [addWriter], or reset with [clearWriters].
 */
object KodioLogging {
    /** Minimum level to emit. Defaults to [LogLevel.None] (fully silent). */
    @Volatile
    var minLevel: LogLevel = LogLevel.None

    private var writers: List<KodioLogWriter> = emptyList()

    /** Returns a snapshot of the currently registered writers. */
    fun writers(): List<KodioLogWriter> = writers

    /** Replaces all writers with the given set. */
    fun setWriters(vararg writers: KodioLogWriter) {
        this.writers = writers.toList()
    }

    /** Appends a writer to the current set. */
    fun addWriter(writer: KodioLogWriter) {
        writers = writers + writer
    }

    /** Removes all writers. Combined with [minLevel] = [LogLevel.None], logging stays silent. */
    fun clearWriters() {
        writers = emptyList()
    }

    /** Configures logging in a single block. */
    fun configure(block: KodioLogging.() -> Unit) {
        block()
    }

    /** Returns whether [level] would be emitted given current configuration. */
    fun isLoggable(level: LogLevel): Boolean =
        level != LogLevel.None &&
            minLevel != LogLevel.None &&
            level.ordinal >= minLevel.ordinal &&
            writers.isNotEmpty()

    internal fun dispatch(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        for (writer in writers) {
            writer.log(level, tag, message, throwable)
        }
    }
}

class KodioLogger internal constructor(private val tag: String) {
    fun trace(message: () -> Any?) = log(LogLevel.Trace, null, message)
    fun debug(message: () -> Any?) = log(LogLevel.Debug, null, message)
    fun info(message: () -> Any?) = log(LogLevel.Info, null, message)
    fun warn(message: () -> Any?) = log(LogLevel.Warn, null, message)
    fun error(message: () -> Any?) = log(LogLevel.Error, null, message)
    fun warn(throwable: Throwable?, message: () -> Any?) = log(LogLevel.Warn, throwable, message)
    fun error(throwable: Throwable?, message: () -> Any?) = log(LogLevel.Error, throwable, message)

    private fun log(level: LogLevel, throwable: Throwable?, message: () -> Any?) {
        if (!KodioLogging.isLoggable(level)) return
        KodioLogging.dispatch(level, tag, message()?.toString() ?: "null", throwable)
    }
}

/** Creates a [KodioLogger] tagged with [name]. */
fun kodioLogger(name: String): KodioLogger = KodioLogger(name)
