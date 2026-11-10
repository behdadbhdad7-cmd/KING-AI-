
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val telegramApiId = System.getenv("TELEGRAM_API_ID") ?: ""
val telegramApiHash = System.getenv("TELEGRAM_API_HASH") ?: ""

android {
    namespace = "com.kingai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingai.assistant"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "TELEGRAM_API_ID",
            "\"$telegramApiId\""
        )

        buildConfigField(
            "String",
            "TELEGRAM_API_HASH",
            "\"$telegramApiHash\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
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
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("io.github.tdlib-android:ktx:0.1.1")
}
