import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google push is built in only when this build has the project's google-services.json (CI writes it from a secret; it is not in the repo).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.churchdrive.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.churchdrive.manager"
        minSdk = 26
        targetSdk = 36
        // The version is set by hand in version.properties; every CI build also counts up (its build number), so a newer
        // build is always recognisable and installs over an older one.
        val appVersion = Properties().apply { file("../version.properties").inputStream().use { load(it) } }.getProperty("version")
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = maxOf(build, 1)
        versionName = appVersion
        buildConfigField("String", "BUILD", "\"$build\"")
        // The commit this build is from (set by CI), shown beside the account icon so a build can be told apart.
        buildConfigField("String", "COMMIT", "\"${(System.getenv("GITHUB_SHA") ?: "dev").take(7)}\"")
        // The WebRTC library is large; phones are 64 or 32 bit ARM.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // A fixed debug key, so each build installs over the last one on a phone.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
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
            storeFile = file(System.getenv("UPLOAD_KEYSTORE_PATH") ?: "upload.keystore")
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD") ?: "churchdrive-upload"
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD") ?: "churchdrive-upload"
        }
    }

    buildTypes {
        debug { buildConfigField("Boolean", "PLAY", "false") }
        release {
            // The Google Play build is updated by Play, not by the GitHub test-build check.
            buildConfigField("Boolean", "PLAY", "true")
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
        buildConfig = true
        compose = true
    }
}

dependencies {
    // The Home Assistant connection, sign-in storage and to-do/Clive data, shared with the watch and to-do apps.
    implementation(project(":core"))
    // Sends the sign-in to a paired watch.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.getstream:stream-webrtc-android:1.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Google's push service: notifications reach the app without it holding its own connection.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-installations")
    implementation("com.google.android.gms:play-services-base:18.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
