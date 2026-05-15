plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.hook.core"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
