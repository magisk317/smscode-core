plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.smscode.hook.core"
}

dependencies {
    api(project(":magisk-xposed-kit:contract"))
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
    val verificationProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:verification"
    } else {
        ":verification"
    }
    val verificationRuntimeProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:verification-runtime"
    } else {
        ":verification-runtime"
    }
    val xposedKitProjectPath = ":magisk-xposed-kit"
    api(project(runtimeContractProjectPath))
    implementation(project(domainProjectPath))
    implementation(project(ruleProjectPath))
    implementation(project(runtimeProjectPath))
    implementation(project(verificationRuntimeProjectPath))
    implementation(project(verificationProjectPath))
    api(project(xposedKitProjectPath))
    api(project(":magisk-xposed-kit:runtime"))
    api(project(":magisk-xposed-kit:capability"))
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.libxposed.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
