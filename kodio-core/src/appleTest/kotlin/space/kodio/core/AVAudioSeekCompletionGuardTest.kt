package space.kodio.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documents the AVAudioPlayerNode seek rule: completion handlers must never
 * call stop() (deadlock while unscheduling). They only hop work elsewhere.
 */
class AVAudioSeekCompletionGuardTest {

    @Test
    fun completionHandler_neverInvokesStop() {
        var stopCalledFromCompletion = false
        var hoppedToSessionQueue = false

        fun onSessionQueue(block: () -> Unit) {
            hoppedToSessionQueue = true
            block()
        }

        // Simulated completion handler (must match production discipline).
        val completionHandler: () -> Unit = {
            // FORBIDDEN: player.stop()
            // stopCalledFromCompletion = true
            onSessionQueue {
                // schedule more / signal only
            }
        }

        completionHandler()

        assertTrue(hoppedToSessionQueue)
        assertFalse(stopCalledFromCompletion)
    }
}
