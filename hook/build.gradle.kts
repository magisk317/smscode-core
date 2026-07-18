plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.hook.core"
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
    val runtimeContractProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:contract"
    } else {
        ":contract"
    }
    val domainProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:domain"
    } else {
        ":domain"
    }
    val xposedKitProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":magisk-xposed-kit"
    } else {
        ":magisk-xposed-kit"
    }
    api(project(runtimeContractProjectPath))
    implementation(project(domainProjectPath))
    api(project(xposedKitProjectPath))
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.libxposed.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
