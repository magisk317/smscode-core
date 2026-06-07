plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.xposed"
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
    val hookCoreProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:hook"
    } else {
        ":hook"
    }
    api(project(hookCoreProjectPath))
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
