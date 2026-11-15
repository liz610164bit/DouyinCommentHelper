plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.commenthelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.commenthelper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
