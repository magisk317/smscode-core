plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.domain"
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
    val ruleCoreProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:smscode-rule-core"
    } else {
        ":smscode-rule-core"
    }
    api(project(ruleCoreProjectPath))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
