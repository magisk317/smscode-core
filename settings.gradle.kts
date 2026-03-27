pluginManagement {
    plugins {
        id("com.android.library") version "9.2.0-alpha06"
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
