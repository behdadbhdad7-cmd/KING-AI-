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
        // شماره ساخت از GitHub Actions می‌آید تا هر نسخه‌ی جدید روی قبلی نصب شود.
        versionCode = (project.findProperty("kingVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1"
    }

    // امضای ثابت برای نسخه‌ی debug: بدون آن، هر ساخت در GitHub Actions با کلید
    // تصادفی جدید امضا می‌شود و اندروید نصب روی نسخه‌ی قبلی را رد می‌کند.
    // فقط وقتی فایل app/debug.keystore وجود داشته باشد (workflow آن را می‌سازد) فعال می‌شود.
    // رمزها از متغیرهای محیطی (GitHub Secrets) خوانده می‌شوند؛ اگر نبودند، مقدار پیش‌فرض debug.
    signingConfigs {
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                val pass = System.getenv("KING_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
                val alias = System.getenv("KING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
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
