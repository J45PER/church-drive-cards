import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Wear OS app. It has the SAME application id and signing key as the phone app (:app), so Google Play treats it as part of
// the same listing, and the phone can hand the watch its sign-in. It is uploaded to the Wear OS form factor of that listing.
android {
    namespace = "com.churchdrive.app.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.churchdrive.manager"
        minSdk = 30
        targetSdk = 35
        val appVersion = Properties().apply { file("../version.properties").inputStream().use { load(it) } }.getProperty("version")
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        // Play wants every APK of a listing to have its own version code: the watch's is the phone's times ten, plus one.
        versionCode = maxOf(build, 1) * 10 + 1
        versionName = appVersion
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Google Play upload key. Play re-signs the app with its own key (Play App Signing), so this key only proves an upload is ours; if it is
    // ever lost or leaked, Play can reset it. CI can use a private one instead (secrets UPLOAD_KEYSTORE_BASE64, UPLOAD_STORE_PASSWORD,
    // UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD, written to UPLOAD_KEYSTORE_PATH); without them the key in the repo is used.
    signingConfigs {
        create("upload") {
            storeFile = file(System.getenv("UPLOAD_KEYSTORE_PATH") ?: "../app/upload.keystore")
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD") ?: "churchdrive-upload"
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD") ?: "churchdrive-upload"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("upload")
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
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
