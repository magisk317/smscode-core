plugins {
    base
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("com.android.kotlin.multiplatform.library") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

val moduleNames = listOf("contract", "capture", "runtime", "engine", "engine/api", "sender", "sender/api", "net", "security", "db", "android")

tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Verify the forwarding repository keeps infrastructure, domain and product boundaries separate."
    inputs.files(moduleNames.flatMap { name ->
        listOf(
            layout.projectDirectory.file("$name/build.gradle.kts"),
            fileTree(layout.projectDirectory.dir("$name/src")) { include("**/*.kt") },
        )
    })
}
