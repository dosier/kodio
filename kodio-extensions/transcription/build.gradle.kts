@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

group = "space.kodio.extensions"
version = "0.0.1"

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

                // Logging
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
    namespace = "space.kodio.transcription"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

