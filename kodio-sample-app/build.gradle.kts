@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val jsAppName = project.name + "-js"
val wasmAppName = project.name + "-wasm"

kotlin {
    androidLibrary {
        namespace = "space.kodio.sample"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    applyDefaultHierarchyTemplate {
        common {
            group("nonWebMacos") {
                withAndroidTarget()
                withIos()
                withJvm()
            }
            group("nonMacos") {
                withAndroidTarget()
                withJvm()
                withIos()
                withJs()
                withWasmJs()
            }
            group("nonIos") {
                withAndroidTarget()
                withJvm()
                withJs()
                withWasmJs()
            }
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    
    jvm("desktop")

    js {
        outputModuleName.set(jsAppName)
        browser {
            commonWebpackConfig {
                outputFileName = "$jsAppName.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(project.rootDir.path)
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set(wasmAppName)
        browser {
            commonWebpackConfig {
                outputFileName = "$wasmAppName.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.rootDir.path)
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting
        val nonMacosMain by getting
        val nonWebMacosMain by getting
        val nonIosMain by getting
        val webMain by getting
        val androidMain by getting {
            dependsOn(nonIosMain)
            dependsOn(nonWebMacosMain)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            api(projects.kodio.kodioCore)
            api(projects.kodio.kodioExtensions.composeMaterial3)
            api(projects.kodio.kodioExtensions.transcription)
            implementation(libs.ktor.client.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            // Stable with CMP 1.11.1 / material3 1.9.0 — NavigationSuiteScaffold
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // FileKit for cross-platform file operations
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
        }
        val iosMain by getting
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        webMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

// Read API key from local.properties (gitignored)
val localPropsFile = rootProject.file("local.properties")
val openaiApiKey: String = if (localPropsFile.exists()) {
    val props = Properties()
    localPropsFile.inputStream().use { props.load(it) }
    props.getProperty("openai.api.key", "")
} else ""

/**
 * Resolve flags that must land on the *application* JVM.
 *
 * IntelliJ's Gradle run config "VM options" (`-D...`) apply to the Gradle daemon,
 * not to [desktopRun] / Compose [run]. We forward them here (and onto JavaExec
 * tasks below). Prefer also `-Pkodio.useJavaSound=true` or env
 * `KODIO_USE_JAVA_SOUND=true`.
 */
fun resolveAppSystemProperty(key: String, envKey: String = key.uppercase().replace('.', '_')): String? =
    System.getProperty(key)
        ?: (findProperty(key) as String?)
        ?: System.getenv(envKey)

val useJavaSoundProp = resolveAppSystemProperty("kodio.useJavaSound", "KODIO_USE_JAVA_SOUND")

fun org.gradle.process.JavaForkOptions.applyKodioSampleJvmArgs() {
    jvmArgs("-Dopenai.api.key=$openaiApiKey")
    if (useJavaSoundProp != null) {
        jvmArgs("-Dkodio.useJavaSound=$useJavaSoundProp")
    }
}

compose.desktop {
    application {
        mainClass = "space.kodio.sample.MainKt"
        jvmArgs("-Dopenai.api.key=$openaiApiKey")
        if (useJavaSoundProp != null) {
            jvmArgs("-Dkodio.useJavaSound=$useJavaSoundProp")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "space.kodio.sample"
            packageVersion = "1.0.0"
        }
    }
}

// KMP `desktopRun` (KotlinJvmRun) is what the IDE launches — not Compose `run`.
// compose.desktop { jvmArgs } does not apply to it; configure JavaExec explicitly.
tasks.withType<JavaExec>().configureEach {
    if (name == "desktopRun" ||
        name == "run" ||
        name.startsWith("hotRun") ||
        name.startsWith("hotDev")
    ) {
        applyKodioSampleJvmArgs()
    }
}