plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.magisk317.smscode.runtime.common"
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
    val domainProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:smscode-domain"
    } else {
        ":smscode-domain"
    }
    val runtimeContractProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:smscode-runtime-contract"
    } else {
        ":smscode-runtime-contract"
    }
    implementation(project(domainProjectPath))
    api(project(runtimeContractProjectPath))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
