package io.github.magisk317.smscode.runtime.contract.packageenv

enum class PackageState {
    NOT_INSTALLED,
    DISABLED,
    ENABLED,
}

data class PackageVersionInfo(
    val versionName: String,
    val versionCode: Long,
)
