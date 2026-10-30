plugins {
    id("magisk.kotlin.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":magisk-xposed-kit:contract"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
