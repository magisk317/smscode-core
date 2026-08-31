plugins {
    id("magisk.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.magisk317.smscode.runtime.common"
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:diagnostics"))
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
    val runtimeContractProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:contract"
    } else {
        ":contract"
    }
    val verificationProjectPath = if (project.path.startsWith(":smscode-core:")) {
        ":smscode-core:verification"
    } else {
        ":verification"
    }
    implementation(project(domainProjectPath))
    implementation(project(ruleProjectPath))
    implementation(project(verificationProjectPath))
    api(project(runtimeContractProjectPath))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
