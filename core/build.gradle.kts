val sharedProjectPrefix = project.parent?.path?.takeIf { it.isNotBlank() } ?: ""
val xposedProjectPath = if (sharedProjectPrefix.isBlank()) {
    ":smscode-xposed-core"
} else {
    "$sharedProjectPrefix:smscode-xposed-core"
}
val domainProjectPath = if (sharedProjectPrefix.isBlank()) {
    ":smscode-domain"
} else {
    "$sharedProjectPrefix:smscode-domain"
}

plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(xposedProjectPath))
    api(project(domainProjectPath))
}
