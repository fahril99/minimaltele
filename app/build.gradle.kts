plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.minimaltelegram"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.minimaltelegram"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0") // Needed for BottomSheet and rounded corners
    implementation("com.github.bumptech.glide:glide:4.16.0") // For avatars and thumbnails
    implementation("androidx.media3:media3-exoplayer:1.3.1") // Video playback
    implementation("androidx.media3:media3-ui:1.3.1") // ExoPlayer UI components
    implementation(files("libs/tdlib.aar"))
}
