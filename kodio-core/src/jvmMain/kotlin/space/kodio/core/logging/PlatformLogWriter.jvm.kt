package space.kodio.core.logging

actual fun platformLogWriter(): KodioLogWriter = JvmPlatformLogWriter

private object JvmPlatformLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val line = formatLogLine(level, tag, message) +
            (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        when (level) {
            LogLevel.Warn, LogLevel.Error -> System.err.println(line)
            else -> System.out.println(line)
        }
    }
}
