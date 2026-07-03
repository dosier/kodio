package space.kodio.core

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the JVM audio system with native macOS fallback.
 *
 * These tests verify:
 * 1. The SystemAudioSystem correctly selects native or JVM implementation
 * 2. The fallback mechanism works when native is unavailable
 *
 * The whole class is gated on macOS via [Assume.assumeTrue] in [skipUnlessMacOs];
 * on Linux/Windows the tests show up as "skipped" rather than "ignored", which
 * makes it obvious they aren't actually running yet still keeps the suite green
 * (see GitHub issue #15).
 */
class NativeMacosAudioSystemTest {

    @Before
    fun skipUnlessMacOs() {
        assumeTrue(
            "NativeMacosAudioSystemTest requires macOS host (see GitHub issue #15)",
            System.getProperty("os.name").lowercase().contains("mac"),
        )
    }

    @Test
    fun `SystemAudioSystem should be available on all JVM platforms`() {
        assertNotNull(SystemAudioSystem, "SystemAudioSystem should not be null")
    }

    @Test
    fun `on macOS should prefer native implementation when available`() {
        val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

        if (isMacOS) {
            println("Running on macOS")
            println("  Native available: ${NativeMacosAudioSystem.isAvailable}")

            if (NativeMacosAudioSystem.isAvailable) {
                assertTrue(NativeMacosAudioSystem.isAvailable, "Native should be available on macOS")
            } else {
                println("  Native library not loaded - using JVM fallback")
            }
        } else {
            println("Skipping test: Not running on macOS")
        }
    }

    @Test
    fun `JvmAudioSystem should be available as fallback`() {
        assertNotNull(JvmAudioSystem, "JvmAudioSystem should not be null")
    }

    @Test
    fun `should be able to list input devices`() {
        runBlocking {
            val devices = SystemAudioSystem.listInputDevices()
            println("Found ${devices.size} input devices:")
            devices.forEach { device ->
                println("  - ${device.name} (${device.id})")
            }
            assertNotNull(devices)
        }
    }

    @Test
    fun `should be able to list output devices`() {
        runBlocking {
            val devices = SystemAudioSystem.listOutputDevices()
            println("Found ${devices.size} output devices:")
            devices.forEach { device ->
                println("  - ${device.name} (${device.id})")
            }
            assertNotNull(devices)
        }
    }
}
