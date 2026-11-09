plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kingai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingai.assistant"
        minSdk = 26
        targetSdk = 33

        versionCode = (project.findProperty("kingVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1"
    }

    signingConfigs {
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                val pass = System.getenv("KING_KEYSTORE_PASSWORD")
                    ?.takeIf { it.isNotBlank() } ?: "android"
                val alias = System.getenv("KING_KEY_ALIAS")
                    ?.takeIf { it.isNotBlank() } ?: "androiddebugkey"

                storeFile = ks
                storePassword = pass
                keyAlias = alias
                keyPassword = pass
            }
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
    // سیستم دوربین
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
}
