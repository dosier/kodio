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
    artifactId = "compose"
    description = "Compose UI components for Kodio audio library"
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "space.kodio.compose"
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
                implementation(libs.bignum)
                implementation(libs.kotlin.logging)
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
