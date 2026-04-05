plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.magisk317.smscode.runtime.common"
    compileSdk = 37

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
    implementation(project(domainProjectPath))
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
