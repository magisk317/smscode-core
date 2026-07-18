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
include(":magisk-xposed-kit")
include(":magisk-xposed-kit:logging")
include(":magisk-xposed-kit:diagnostics")
include(":domain")
include(":contract")
include(":verification")
include(":runtime")
include(":rule")
include(":hook")

project(":magisk-xposed-kit").projectDir = file("../magisk-xposed-kit")
project(":magisk-xposed-kit:logging").projectDir = file("../magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("../magisk-xposed-kit/diagnostics")
