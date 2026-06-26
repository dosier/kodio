package space.kodio.core.logging

import platform.Foundation.NSLog

actual fun platformLogWriter(): KodioLogWriter = ApplePlatformLogWriter

private object ApplePlatformLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val line = formatLogLine(level, tag, message) +
            (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        NSLog("%s", line)
    }
}
