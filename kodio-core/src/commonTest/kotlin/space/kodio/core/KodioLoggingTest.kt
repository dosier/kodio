package space.kodio.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import space.kodio.core.logging.KodioLogWriter
import space.kodio.core.logging.KodioLogging
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.kodioLogger

internal class KodioLoggingTest {

    private data class LogRecord(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    private lateinit var captured: MutableList<LogRecord>

    @BeforeTest
    fun setUp() {
        resetLogging()
        captured = mutableListOf()
    }

    @AfterTest
    fun tearDown() {
        resetLogging()
    }

    private fun resetLogging() {
        KodioLogging.clearWriters()
        KodioLogging.minLevel = LogLevel.None
    }

    private fun capturingWriter(): KodioLogWriter = KodioLogWriter { level, tag, message, throwable ->
        captured.add(LogRecord(level, tag, message, throwable))
    }

    @Test
    fun `silent by default`() {
        kodioLogger("T").info { "x" }
        assertTrue(captured.isEmpty())
        assertFalse(KodioLogging.isLoggable(LogLevel.Error))
    }

    @Test
    fun `respects minLevel gating`() {
        KodioLogging.configure {
            minLevel = LogLevel.Warn
            addWriter(capturingWriter())
        }
        val logger = kodioLogger("T")
        logger.debug { "debug-msg" }
        logger.info { "info-msg" }
        logger.warn { "warn-msg" }
        logger.error { "error-msg" }

        assertEquals(2, captured.size)
        assertEquals(LogLevel.Warn, captured[0].level)
        assertEquals("warn-msg", captured[0].message)
        assertEquals(LogLevel.Error, captured[1].level)
        assertEquals("error-msg", captured[1].message)
    }

    @Test
    fun `delivers to writer at or above minLevel`() {
        KodioLogging.configure {
            minLevel = LogLevel.Debug
            addWriter(capturingWriter())
        }
        kodioLogger("MyTag").debug { "hello-debug" }

        assertEquals(1, captured.size)
        assertEquals(LogLevel.Debug, captured[0].level)
        assertEquals("MyTag", captured[0].tag)
        assertEquals("hello-debug", captured[0].message)
    }

    @Test
    fun `passes throwable through`() {
        KodioLogging.configure {
            minLevel = LogLevel.Error
            addWriter(capturingWriter())
        }
        val cause = RuntimeException("bad")
        kodioLogger("T").error(cause) { "failed" }

        assertEquals(1, captured.size)
        assertEquals("failed", captured[0].message)
        assertEquals("bad", captured[0].throwable?.message)
    }

    @Test
    fun `lazy message not evaluated when gated`() {
        KodioLogging.configure {
            minLevel = LogLevel.Warn
            addWriter(capturingWriter())
        }
        kodioLogger("T").debug { throw IllegalStateException("should not evaluate") }
        assertTrue(captured.isEmpty())
    }
}
