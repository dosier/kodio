plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinJsPlainObjects) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.dokka)
}

dependencies {
    dokka(project(":kodio-core"))
    dokka(project(":kodio-extensions:compose"))
    dokka(project(":kodio-extensions:compose-material3"))
    dokka(project(":kodio-extensions:ktor"))
    dokka(project(":kodio-extensions:transcription"))
    dokka(project(":kodio-native:audio-permissions"))
    dokka(project(":kodio-native:audio-processing"))
}

dokka {
    moduleName.set("Kodio")
    pluginsConfiguration.html {
        footerMessage.set("Kodio API documentation. <a href=\"https://github.com/dosier/kodio\">GitHub</a>")
    }
}
