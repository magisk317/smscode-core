plugins {
    id("magisk.kotlin.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    val verificationProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:verification"
    } else {
        ":verification"
    }
    api(project(verificationProjectPath))
    implementation(libs.kotlinx.serialization.json)
    // Flow appears in the repository contract, so consumers see it transitively.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
