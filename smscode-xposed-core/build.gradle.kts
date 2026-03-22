plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.xposed"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.0")
    compileOnly("androidx.annotation:annotation:1.9.1")
}
