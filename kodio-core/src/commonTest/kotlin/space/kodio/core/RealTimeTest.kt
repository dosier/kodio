package space.kodio.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [block] on [Dispatchers.Default] with real (not virtual) time.
 *
 * For tests of sessions that launch their own work on [Dispatchers.Default]:
 * `runTest`'s virtual clock cannot advance that work, and `runBlocking` does not
 * exist on JS/Wasm. This works on every target (it returns a Promise on web).
 */
internal fun runRealTimeTest(
    timeout: Duration = 30.seconds,
    block: suspend CoroutineScope.() -> Unit,
): TestResult = runTest(timeout = timeout) {
    withContext(Dispatchers.Default) { block() }
}
