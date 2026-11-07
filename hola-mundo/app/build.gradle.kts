plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayalalopes.holamundo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ayalalopes.holamundo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Keystore de depuración compartido (en la raíz del repo) para que
    // cada nueva versión se pueda instalar encima de la anterior.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
