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
    val hookCoreProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:smscode-hook-core"
    } else {
        ":smscode-hook-core"
    }
    implementation(project(hookCoreProjectPath))
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
