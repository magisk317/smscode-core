plugins {
    base
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

val boundaryModuleNames = listOf("domain", "rule", "contract", "verification", "runtime", "hook")

val verifyModuleBoundaries = tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Verify pure Kotlin/JVM and Android module ownership boundaries."

    val moduleRoot = project.layout.projectDirectory.asFile
    inputs.files(
        boundaryModuleNames.flatMap { moduleName ->
            listOf(
                moduleRoot.resolve("$moduleName/build.gradle.kts"),
                fileTree(moduleRoot.resolve("$moduleName/src")) { include("**/*.kt") },
            )
        },
    )

    doLast {
        val pureJvmModules = listOf("domain", "rule", "contract", "verification")
        val androidModules = listOf("runtime", "hook")
        val violations = mutableListOf<String>()
        val androidImport = Regex("""^\s*import\s+(android|androidx)\.""")
        val forbiddenPurePluginMarkers = listOf(
            "magisk.android.",
            "com.android.library",
            "org.jetbrains.kotlin.android",
        )
        val forbiddenPureDependencyMarkers = listOf(
            "libs.androidx.",
            "project(\":runtime\")",
            "project(\":hook\")",
            "project(\":smscode-core:runtime\")",
            "project(\":smscode-core:hook\")",
        )

        pureJvmModules.forEach { moduleName ->
            val moduleDir = moduleRoot.resolve(moduleName)
            val buildFile = moduleDir.resolve("build.gradle.kts")
            val buildText = buildFile.readText()

            if (!buildText.contains("id(\"magisk.kotlin.library\")")) {
                violations += "$moduleName must apply magisk.kotlin.library"
            }
            (forbiddenPurePluginMarkers + forbiddenPureDependencyMarkers).forEach { marker ->
                if (buildText.contains(marker)) {
                    violations += "$moduleName/build.gradle.kts contains forbidden boundary marker: $marker"
                }
            }

            moduleDir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    sourceFile.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (androidImport.containsMatchIn(line)) {
                                violations +=
                                    "${sourceFile.relativeTo(moduleRoot)}:${index + 1} imports Android from a pure JVM module"
                            }
                        }
                    }
                }
        }

        androidModules.forEach { moduleName ->
            val buildText = moduleRoot.resolve("$moduleName/build.gradle.kts").readText()
            if (!buildText.contains("id(\"magisk.android.library\")")) {
                violations += "$moduleName must apply magisk.android.library"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "SmsCode module boundary violations:\n${violations.joinToString("\n")}",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyModuleBoundaries)
}
