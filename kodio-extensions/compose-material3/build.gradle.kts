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
}

group = "space.kodio.extensions"

kodioPublishing {
    artifactId = "compose-material3"
    description = "Material 3 UI components for Kodio audio library"
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "space.kodio.extensions.compose.material3"
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
                api(projects.kodio.kodioExtensions.compose)
                // Project Libraries
                implementation(projects.kodio.kodioCore)
                implementation(compose.material3)
                implementation(libs.material.icons)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

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
