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
        versionCode = 1
        versionName = "0.1"
    }
}
