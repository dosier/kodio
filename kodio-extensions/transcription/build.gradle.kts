@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("kodio-publish-convention")
}

group = "space.kodio.extensions"

kodioPublishing {
    artifactId = "transcription"
    description = "Audio transcription extension for Kodio using OpenAI Whisper"
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "space.kodio.transcription"
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
                // Kodio core for AudioFlow integration
                implementation(projects.kodio.kodioCore)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Ktor for WebSocket connections to cloud providers
                implementation(libs.ktor.client.core)
                implementation("io.ktor:ktor-client-websockets:${libs.versions.ktor3.get()}")
                implementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor3.get()}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor3.get()}")

                // Serialization for API messages
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val jvmTest by getting {
            dependencies {
                // JUnit 4 runner + Assume for the live OpenAI integration test.
                implementation(libs.kotlin.testJunit)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.core.ktx)
            }
        }
        appleMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        @Suppress("unused")
        val webMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    // Live OpenAI integration tests may load multi-minute WAVs; avoid OOM on default 512m test heap.
    maxHeapSize = "4g"
}

