import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shani.profilepilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shani.profilepilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "1.7.0"
    }

    // Persistent keystore (same cert as Play-installed app = updates work).
    // Only applied when the keystore file exists (local VM / CI with secrets).
    // Otherwise Gradle falls back to the default debug keystore.
    val persistentKeystore = file("/home/hatch/workspace/android-keystore/proxypilot.jks")
    if (persistentKeystore.exists()) {
        signingConfigs {
            create("persistent") {
                // Keystore password is NEVER hardcoded. It is read from
                // local.properties (storePassword=...) or the
                // PROFILEPILOT_STOREPASS environment variable.
                val p = Properties()
                val f = rootProject.file("local.properties")
                if (f.exists()) {
                    f.inputStream().use { p.load(it) }
                }
                val sp = p.getProperty("storePassword", System.getenv("PROFILEPILOT_STOREPASS"))
                storeFile = persistentKeystore
                storePassword = sp
                keyAlias = "proxypilot"
                keyPassword = sp
            }
        }
    }

    buildTypes {
        release {
            if (persistentKeystore.exists()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (persistentKeystore.exists()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.webkit:webkit:1.10.1")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Room (KSP)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // org.json and javax.crypto are part of the Android SDK — no dependency needed.
}
