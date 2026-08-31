plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.hook.core"
}

dependencies {
    val runtimeContractProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:contract"
    } else {
        ":contract"
    }
    val domainProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:domain"
    } else {
        ":domain"
    }
    val ruleProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:rule"
    } else {
        ":rule"
    }
    val runtimeProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:runtime"
    } else {
        ":runtime"
    }
    val xposedKitProjectPath = ":magisk-xposed-kit"
    api(project(runtimeContractProjectPath))
    implementation(project(domainProjectPath))
    implementation(project(ruleProjectPath))
    implementation(project(runtimeProjectPath))
    api(project(xposedKitProjectPath))
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.libxposed.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
