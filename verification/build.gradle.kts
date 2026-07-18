plugins {
    id("com.android.library")
}

val runtimeContractProjectPath = parent?.path
    ?.takeUnless { it == ":" }
    ?.let { "$it:contract" }
    ?: ":contract"
val ruleCoreProjectPath = parent?.path
    ?.takeUnless { it == ":" }
    ?.let { "$it:rule" }
    ?: ":rule"

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
    api(project(runtimeContractProjectPath))
    implementation(project(ruleCoreProjectPath))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
