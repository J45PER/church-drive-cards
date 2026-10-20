import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Church Drive To-Do: just the to-do lists, as a small app of its own (its own application id and Play listing). It signs in to
// Home Assistant itself and shares the connection and to-do code with the other apps through :core.
android {
    namespace = "com.churchdrive.todo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.churchdrive.todo"
        minSdk = 26
        targetSdk = 35
        val appVersion = Properties().apply { file("version.properties").inputStream().use { load(it) } }.getProperty("version")
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = maxOf(build, 1)
        versionName = appVersion
        buildConfigField("String", "BUILD", "\"$build\"")
        // The commit this build is from (set by CI), shown in About so a build can be told apart.
        buildConfigField("String", "COMMIT", "\"${(System.getenv("GITHUB_SHA") ?: "dev").take(7)}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
