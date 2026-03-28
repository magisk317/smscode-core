pluginManagement {
    plugins {
        id("com.android.library") version "9.1.0"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "smscode-core"
include(":smscode-xposed-core")
include(":smscode-domain")
include(":smscode-verification-core")
