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
    val ruleCoreProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:rule"
    } else {
        ":rule"
    }
    api(project(ruleCoreProjectPath))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
