plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optionaler fester Signaturschlüssel (aus GitHub-Secrets, siehe README).
// Ohne ihn signiert Gradle mit einem zufälligen Debug-Schlüssel, dann lässt
// sich eine neue Version nur nach Deinstallation der alten installieren.
val signingStore = System.getenv("SIGNING_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "de.glut.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.glut.launcher"
        // Fire OS 7 = Android 9 (API 28), Fire OS 8 = Android 11 (API 30)
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signingStore != null) {
            create("sideload") {
                storeFile = signingStore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (signingStore != null) signingConfig = signingConfigs.getByName("sideload")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
