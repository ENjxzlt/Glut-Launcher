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
        // Auf GitHub zählt jeder Build hoch; daran erkennt der Launcher Updates.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        // Release-Tags (v1.0.0) setzen den Namen, sonst Entwicklungs-Build.
        versionName = System.getenv("GLUT_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "dev-$build"
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
        // Auf dem Fire TV Stick Lite ist der Debug-Build spürbar träge; ausgeliefert wird release.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        // Lint-Prüfungen im Release-Build würden an Play-Store-Vorgaben scheitern,
        // die für Sideloading keine Rolle spielen.
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    // Installiert die mitgelieferten Startprofile von Compose → schnellerer Start
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
