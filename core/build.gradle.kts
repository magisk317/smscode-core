import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    compileOnly("io.github.libxposed:api:101.0.0")
    // AndroidX annotations if needed by moved classes
    compileOnly("androidx.annotation:annotation:1.9.1")
    // Xposed legacy API: resolve via local stub jar if present
    compileOnly(files("libs/xposed-stub.jar"))
}
