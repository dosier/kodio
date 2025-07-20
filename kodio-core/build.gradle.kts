@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinJsPlainObjects)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "space.kodio"
version = "0.0.2"

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
        compilerOptions {
            freeCompilerArgs.add("-Xwasm-attach-js-exception")
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io)
                implementation(libs.bignum)
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
        jsMain {
            dependencies {
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

android {
    namespace = "space.kodio"
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
tasks.named<Copy>("jvmProcessResources") {
    // Make sure the native libraries are built before this task runs
    dependsOn(
        nativePermissionsProject.tasks.named("macosX64Binaries"),
        nativePermissionsProject.tasks.named("macosArm64Binaries"),
        nativePermissionsProject.tasks.named("mingwX64Binaries"),
    )
    fun target(name: String) =
        nativePermissionsProject.layout.buildDirectory.dir("bin/$name/audiopermissionsReleaseShared")
    from(target("macosArm64")) {
        include("libaudiopermissions.dylib")
        into("$nativeLibsDir/macos-aarch64")
    }
    from(target("macosX64")) {
        include("libaudiopermissions.dylib")
        into("$nativeLibsDir/macos-x86-64")
    }
    from(target("mingwX64")) {
        include("audiopermissions.dll")
        into("$nativeLibsDir/windows-x86-64")
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "core", version.toString())

    pom {
        name = "Kodio"
        description = "A multiplatform library for audio recording/playback."
        inceptionYear = "2025"
        url = "https://github.com/dosier/kodio"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "dosier"
                name = "Stan"
                url = "https://github.com/dosier"
            }
        }
        scm {
            url = "https://github.com/dosier/kodio"
            connection = "scm:git:git://github.com/dosier/kodio.git"
            developerConnection = "scm:git:ssh://git@github.com/dosier/kodio.git"
        }
    }
}
