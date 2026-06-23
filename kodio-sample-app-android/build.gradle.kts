plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "space.kodio.sample.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "space.kodio.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":kodio-sample-app"))
    implementation(project(":kodio-core"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.components.uiToolingPreview)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs.compose)
    debugImplementation(compose.uiTooling)
}
