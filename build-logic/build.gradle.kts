plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Use explicit dependency since version catalog may not be fully available in build-logic
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.35.0")
}

