plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Shared by the phone app, the watch app and the to-do app: the Home Assistant connection, sign-in storage, to-do and Clive data.
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
    // The look the phone app and the to-do app share (Theme.kt, StateColors.kt).
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    api("androidx.security:security-crypto:1.1.0-alpha06")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // The update notice every app shows (UpdateNotice.kt), and Google Play's own update screen for Play installs (PlayUpdate.kt).
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.play:app-update:2.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
