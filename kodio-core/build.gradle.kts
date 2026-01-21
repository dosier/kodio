@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinJsPlainObjects)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
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
    androidLibrary {
        namespace = "space.kodio"
        compileSdk = 36
        minSdk = 24
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

tasks.named<Copy>("jvmProcessResources") {
    // Make sure the native libraries are built before this task runs
    dependsOn(
        // Permissions
        nativePermissionsProject.tasks.named("macosX64Binaries"),
        nativePermissionsProject.tasks.named("macosArm64Binaries"),
        nativePermissionsProject.tasks.named("mingwX64Binaries"),
        // Processing
        nativeProcessingProject.tasks.named("macosX64Binaries"),
        nativeProcessingProject.tasks.named("macosArm64Binaries"),
    )
    fun permissions(target: String) =
        nativePermissionsProject.layout.buildDirectory.dir("bin/$target/audiopermissionsReleaseShared")
    from(permissions("macosArm64")) {
        include("libaudiopermissions.dylib")
        into("$nativeLibsDir/macos-aarch64")
    }
    from(permissions("macosX64")) {
        include("libaudiopermissions.dylib")
        into("$nativeLibsDir/macos-x86-64")
    }
    from(permissions("mingwX64")) {
        include("audiopermissions.dll")
        into("$nativeLibsDir/windows-x86-64")
    }
    fun processing(target: String) =
        nativeProcessingProject.layout.buildDirectory.dir("bin/$target/audioprocessingReleaseShared")
    from(processing("macosArm64")) {
        include("libaudioprocessing.dylib")
        into("$nativeLibsDir/macos-aarch64")
    }
    from(processing("macosX64")) {
        include("libaudioprocessing.dylib")
        into("$nativeLibsDir/macos-x86-64")
    }
}

