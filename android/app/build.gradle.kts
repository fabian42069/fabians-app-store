plugins {
    id("com.android.application")
}

android {
    namespace = "nl.fabian42069.appstore"
    compileSdk = 34

    defaultConfig {
        applicationId = "nl.fabian42069.appstore"
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
}
