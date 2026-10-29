plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    android {
        namespace = "io.github.magisk317.forward.engine"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":magisk-forward-core:contract"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
    }
}
