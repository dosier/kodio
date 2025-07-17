import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    macosArm64()
    macosX64()
    mingwX64()

    // Configure all native targets to build a shared library
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        // We name the output library "audioprocessing"
        // This will produce audioprocessing.dll and libaudioprocessing.dylib
        binaries.sharedLib("audioprocessing", listOf(NativeBuildType.RELEASE)) {
            if (target.konanTarget == KonanTarget.MINGW_X64) {
                // For Windows, we need to link against the Windows Multimedia library
                // which contains the waveInOpen function.
                linkerOpts.add("-lwinmm")
            }
        }
    }
}