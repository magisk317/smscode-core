package io.github.magisk317.smscode.runtime.common.update

typealias GithubReleaseInfo = io.github.magisk317.smscode.runtime.contract.update.GithubReleaseInfo
typealias VersionLog = io.github.magisk317.smscode.runtime.contract.update.VersionLog
typealias UpgradeApkAsset = io.github.magisk317.smscode.runtime.contract.update.UpgradeApkAsset
typealias UpgradeInfo = io.github.magisk317.smscode.runtime.contract.update.UpgradeInfo
typealias GithubUpdateConfig = io.github.magisk317.smscode.runtime.contract.update.GithubUpdateConfig

sealed class UpgradeCheckResult {
    data class Structured(val info: UpgradeInfo) : UpgradeCheckResult()
    data class ReleaseLink(val release: GithubReleaseInfo) : UpgradeCheckResult()
    data object NoUpdate : UpgradeCheckResult()
    data class CheckFailed(val message: String? = null) : UpgradeCheckResult()
}
