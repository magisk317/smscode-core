plugins {
    id("magisk.android.library")
    id("magisk.android.room")
}

android { namespace = "io.github.magisk317.forward.db" }

dependencies {
    api(project(":magisk-forward-core:contract"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
