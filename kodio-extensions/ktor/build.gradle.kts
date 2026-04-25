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
