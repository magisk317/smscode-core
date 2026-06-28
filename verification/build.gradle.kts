plugins {
    id("com.android.library")
}

val smscodeXposedCorePath = parent?.path
    ?.takeUnless { it == ":" }
    ?.let { "$it:hook" }
    ?: ":hook"
val runtimeContractProjectPath = parent?.path
    ?.takeUnless { it == ":" }
    ?.let { "$it:contract" }
    ?: ":contract"

android {
    namespace = "io.github.magisk317.smscode.verification"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaBytecode.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(runtimeContractProjectPath))
    implementation(project(smscodeXposedCorePath))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
