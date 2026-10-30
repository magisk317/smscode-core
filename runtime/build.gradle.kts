plugins { id("magisk.android.library") }

android {
    namespace = "io.github.magisk317.forward.runtime"
    defaultConfig { missingDimensionStrategy("distribution", "github") }
}

dependencies {
    api(project(":magisk-forward-core:contract"))
    implementation(project(":magisk-forward-core:engine"))
    implementation(project(":magisk-forward-core:db"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(project(":magisk-xposed-kit:contract"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
