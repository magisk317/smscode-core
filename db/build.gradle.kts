plugins { id("magisk.android.library") }

android { namespace = "io.github.magisk317.forward.db" }

dependencies {
    api(project(":magisk-forward-core:contract"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
