@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("kodio-publish-convention")
}

group = "space.kodio.extensions"

kodioPublishing {
    artifactId = "ktor"
    description = "Ktor client/server extensions for streaming Kodio AudioFlow over WebSocket and SSE"
}

kotlin {
    jvm()
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

    macosX64()
    macosArm64()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }
    js { browser() }
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kodio.kodioCore)
                api(libs.ktor.client.core)
                api(libs.ktor.client.websockets)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io)
                implementation(libs.kotlin.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.slf4j.simple)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.androidx.core.ktx)
            }
        }
        appleMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:${libs.versions.ktor3.get()}")
            }
        }
        @Suppress("unused")
        val webMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:${libs.versions.ktor3.get()}")
            }
        }
    }
}

android {
    namespace = "space.kodio.extensions.ktor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Linking the Kotlin/Native test binaries for the Ktor extension on Apple
// targets currently trips an internal Kotlin/Native compiler assertion
// ("Lowering ReturnsInsertion: phases [Autobox] are required, but not
// satisfied"), pulled in via ktor-client-darwin. The main klibs link
// fine and the JVM tests (which exercise the actual round-trip wire
// format end-to-end) cover the same code paths. Disable the test
// link/run tasks on Apple targets until upstream Kotlin/Native is happy
// with this combination.
val nativeTestTaskNames = setOf(
    "linkDebugTestMacosArm64",
    "linkDebugTestMacosX64",
    "linkDebugTestIosArm64",
    "linkDebugTestIosX64",
    "linkDebugTestIosSimulatorArm64",
    "macosArm64Test",
    "macosX64Test",
    "iosSimulatorArm64Test",
    "iosX64Test",
)
tasks.matching { it.name in nativeTestTaskNames }.configureEach { enabled = false }
