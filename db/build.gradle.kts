plugins {
    id("magisk.android.library")
    id("magisk.android.room")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "io.github.magisk317.smscode.db.core"
}

dependencies {
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(project(":magisk-xposed-kit:contract"))
    val runtimeContractProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:contract"
    } else {
        ":contract"
    }
    val runtimeProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:runtime"
    } else {
        ":runtime"
    }
    api(project(runtimeContractProjectPath))
    implementation(project(runtimeProjectPath))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
