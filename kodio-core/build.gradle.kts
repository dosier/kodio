@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinJsPlainObjects)
    alias(libs.plugins.androidLibrary)
    id("kodio-publish-convention")
}

group = "space.kodio"

kodioPublishing {
    artifactId = "core"
    description = "A multiplatform library for audio recording and playback"
}

kotlin {
    jvm()
    jvmToolchain(21)
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    listOf(
        macosX64(),
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
                implementation(libs.kotlin.logging)
                api(libs.bignum)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.slf4j.simple)
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

android {
    namespace = "space.kodio.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
            nativePermissionsProject.tasks.named("macosX64Binaries"),
            nativePermissionsProject.tasks.named("macosArm64Binaries"),
            nativeProcessingProject.tasks.named("macosX64Binaries"),
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
        from(permissions("macosX64")) {
            include("libaudiopermissions.dylib")
            into("$nativeLibsDir/macos-x86-64")
        }
        from(processing("macosArm64")) {
            include("libaudioprocessing.dylib")
            into("$nativeLibsDir/macos-aarch64")
        }
        from(processing("macosX64")) {
            include("libaudioprocessing.dylib")
            into("$nativeLibsDir/macos-x86-64")
        }
    }
    if (isWindowsHost) {
        from(permissions("mingwX64")) {
            include("audiopermissions.dll")
            into("$nativeLibsDir/windows-x86-64")
        }
    }
}

