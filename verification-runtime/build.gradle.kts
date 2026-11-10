plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.verification.runtime"
}

dependencies {
    val verificationProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:verification"
    } else {
        ":verification"
    }
    implementation(project(verificationProjectPath))
    implementation(project(":magisk-xposed-kit:contract"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
