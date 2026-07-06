@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinJsPlainObjects)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    id("kodio-publish-convention")
    id("kodio-dokka-convention")
}

group = "space.kodio"

kodioPublishing {
    artifactId = "core"
    description = "A multiplatform library for audio recording and playback"
}

kotlin {
    jvm()
    // JDK 22 finalized the Foreign Function and Memory API (JEP 454) used by the
    // native macOS path. Building on 22+ compiles those calls as a final (non-preview)
    // API. Consumers of the jvm artifact therefore need a Java 22+ runtime.
    jvmToolchain(22)
    androidLibrary {
        namespace = "space.kodio.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    listOf(
        macosArm64()
    ).forEach { macosTarget ->
        macosTarget.binaries.executable {
            entryPoint = "main"  // Change to "main" for full recording+playback test
        }
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }
    js {
        browser()
    }
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io)
                api(libs.bignum)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
            }
        }
        @Suppress("unused")
        val webMain by getting {
            dependencies {
                implementation(libs.kotlin.browser.v202575)
            }
        }
    }
}

val nativeLibsDir = "space/kodio/core/natives"
val nativePermissionsProject = project(":kodio-native:audio-permissions")
val nativeProcessingProject = project(":kodio-native:audio-processing")

// macOS native targets can only be cross-compiled on macOS hosts. On Linux/Windows
// CI we still want jvmTest / publishing to work without dragging in (and failing)
// the macOS dylib build. Skip those dependencies & copy steps when off-host.
// See: https://github.com/dosier/kodio/issues/15
val isMacOsHost = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val isWindowsHost = org.gradle.internal.os.OperatingSystem.current().isWindows

tasks.named<Copy>("jvmProcessResources") {
    if (isMacOsHost) {
        dependsOn(
            nativePermissionsProject.tasks.named("macosArm64Binaries"),
            nativeProcessingProject.tasks.named("macosArm64Binaries"),
        )
    }
    if (isWindowsHost) {
        dependsOn(nativePermissionsProject.tasks.named("mingwX64Binaries"))
    }

    fun permissions(target: String) =
        nativePermissionsProject.layout.buildDirectory.dir("bin/$target/audiopermissionsReleaseShared")
    fun processing(target: String) =
        nativeProcessingProject.layout.buildDirectory.dir("bin/$target/audioprocessingReleaseShared")

    if (isMacOsHost) {
        from(permissions("macosArm64")) {
            include("libaudiopermissions.dylib")
            into("$nativeLibsDir/macos-aarch64")
        }
        from(processing("macosArm64")) {
            include("libaudioprocessing.dylib")
            into("$nativeLibsDir/macos-aarch64")
        }
    }
    if (isWindowsHost) {
        from(permissions("mingwX64")) {
            include("audiopermissions.dll")
            into("$nativeLibsDir/windows-x86-64")
        }
    }
}

