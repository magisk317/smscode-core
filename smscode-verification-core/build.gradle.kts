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
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("dev.mokkery:mokkery-runtime-jvm:3.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
