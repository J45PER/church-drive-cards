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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.churchdrive.todo"
        minSdk = 26
        targetSdk = 36
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
        // Debug builds are the GitHub test builds (they update themselves from there); the release build is the Google Play one (Play updates it).
        debug { buildConfigField("Boolean", "PLAY", "false") }
        release {
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
    // The release (Play) build is not held up by lint's fatal-error pass; CI still runs the unit tests.
    lint { checkReleaseBuilds = false }
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
