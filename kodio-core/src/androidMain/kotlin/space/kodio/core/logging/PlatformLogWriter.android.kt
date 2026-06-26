package space.kodio.core.logging

import android.util.Log

actual fun platformLogWriter(): KodioLogWriter = AndroidPlatformLogWriter

private object AndroidPlatformLogWriter : KodioLogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.Trace -> Log.v(tag, message, throwable)
            LogLevel.Debug -> Log.d(tag, message, throwable)
            LogLevel.Info -> Log.i(tag, message, throwable)
            LogLevel.Warn -> Log.w(tag, message, throwable)
            LogLevel.Error -> Log.e(tag, message, throwable)
            LogLevel.None -> Unit
        }
    }
}
