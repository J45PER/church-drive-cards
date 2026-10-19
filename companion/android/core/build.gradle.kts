plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Shared by the phone app, the watch app and the to-do app: the Home Assistant connection, sign-in storage, to-do and Jarvis data.
// Package names are unchanged (com.churchdrive.app.*), so moving code here never changes an import.
android {
    namespace = "com.churchdrive.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    api(composeBom)
    // Only the runtime (state, composition locals): the screens stay in each app.
    api("androidx.compose.runtime:runtime")
    api("androidx.security:security-crypto:1.1.0-alpha06")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
