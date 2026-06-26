package space.kodio.core.logging

actual fun platformLogWriter(): KodioLogWriter = WebPlatformLogWriter

private object WebPlatformLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val line = formatLogLine(level, tag, message) +
            (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        println(line)
    }
}
