plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.xposed"
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
    compileOnly("io.github.libxposed:api:101.0.0")
    compileOnly("androidx.annotation:annotation:1.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
