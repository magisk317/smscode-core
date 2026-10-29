plugins { id("magisk.android.library") }

android { namespace = "io.github.magisk317.forward.capture" }

dependencies {
    implementation(project(":magisk-forward-core:contract"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:runtime"))
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
