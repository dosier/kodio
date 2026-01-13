package space.kodio.core.util

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Create a logger for the enclosing class.
 * Uses reified generics for type-safe class name extraction.
 * 
 * Usage:
 * ```
 * class MyClass {
 *     private val logger = classLogger()
 *     
 *     fun doSomething() {
 *         logger.info { "Doing something" }
 *     }
 * }
 * ```
 */
internal inline fun <reified T> T.classLogger() = KotlinLogging.logger(T::class.simpleName ?: "Unknown")

/**
 * Create a named logger for file-level or component use.
 * Preferred when logging from top-level functions or objects.
 * 
 * Usage:
 * ```
 * private val logger = namedLogger("SyncManager")
 * 
 * fun processSomething() {
 *     logger.debug { "Processing..." }
 * }
 * ```
 */
internal fun namedLogger(name: String) = KotlinLogging.logger(name)
