@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("kodio-publish-convention")
    id("kodio-dokka-convention")
}

group = "space.kodio.extensions"

kodioPublishing {
    artifactId = "compose"
    description = "Compose UI components for Kodio audio library"
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "space.kodio.extensions.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()

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
    js {
        browser()
    }
    wasmJs {
        browser()
    }
    sourceSets {
        commonMain {
            dependencies {
                // Project Libraries
                implementation(projects.kodio.kodioCore)
                // Compose Libraries
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
        jvmTest {
            dependencies {
                // Skiko runtime needed for Compose UI tests on JVM
                implementation(compose.desktop.currentOs)
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

// Compose UI tests (`runComposeUiTest`) only have a working harness on
// the JVM target in this matrix:
//   * `jsBrowserTest` / `wasmJsBrowserTest`: fail with a ReferenceError
//     sourced from karma+webpack-generated commons.js (43/43 tests).
//   * `testDebugUnitTest` / `testReleaseUnitTest` (Android unit tests on
//     the host JVM): blow up with NullPointerException because the
//     Android stub runtime does not implement the Compose UI test
//     environment.
// Keep the commonTest sources unchanged so these tests still run under
// `:kodio-extensions:compose:jvmTest`, but disable the broken browser
// and Android unit-test tasks until upstream Compose Multiplatform
// support stabilises. Tracked in GitHub issues #12 / #14 follow-ups.
tasks.matching {
    it.name == "jsBrowserTest" ||
        it.name == "wasmJsBrowserTest" ||
        it.name == "testDebugUnitTest" ||
        it.name == "testReleaseUnitTest"
}.configureEach { enabled = false }

// Compose ui-uikit (CMP 1.11) references iOS 26 SDK symbols (e.g. UIViewLayoutRegion)
// that are unavailable in CI's Xcode toolchain, so linking the Apple test
// binaries fails. These modules have no real Kotlin/Native tests (Compose UI
// tests only run on the JVM target), so disable the Apple native test tasks.
val appleNativeTestTaskNames = setOf(
    "linkDebugTestMacosArm64",
    "linkDebugTestIosArm64",
    "linkDebugTestIosSimulatorArm64",
    "macosArm64Test",
    "iosSimulatorArm64Test",
)
tasks.matching { it.name in appleNativeTestTaskNames }.configureEach { enabled = false }
