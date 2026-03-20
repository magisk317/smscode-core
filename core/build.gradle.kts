import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 32
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // libxposed API for hook implementations (compile-only)
    compileOnly(libs.libxposed.api)
    // AndroidX annotations if needed by moved classes
    compileOnly(libs.androidx.annotation)
}
