plugins {
    id("com.android.library")
    id("dev.mokkery")
}

val smscodeXposedCorePath = parent?.path
    ?.takeUnless { it == ":" }
    ?.let { "$it:smscode-xposed-core" }
    ?: ":smscode-xposed-core"

android {
    namespace = "io.github.magisk317.smscode.verification"
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

mokkery {
    defaultMockMode.set(dev.mokkery.MockMode.autofill)
    ignoreFinalMembers.set(true)
}

dependencies {
    implementation(project(smscodeXposedCorePath))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mokkery.runtime-jvm)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
