plugins { id("magisk.android.library") }

android { namespace = "io.github.magisk317.forward.runtime" }

dependencies {
    api(project(":magisk-forward-core:contract"))
    implementation(project(":magisk-forward-core:engine"))
    implementation(project(":magisk-forward-core:db"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(project(":smscode-core:contract"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:db"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
