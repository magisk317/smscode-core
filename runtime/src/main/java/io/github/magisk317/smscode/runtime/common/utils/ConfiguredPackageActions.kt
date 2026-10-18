package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context

/**
 * Product-bound facade over [PackageActions]. Parent apps provide resources and URLs once, then
 * inherit the shared operation surface without duplicating forwarding methods.
 */
open class ConfiguredPackageActions(
    private val config: PackageActionConfig,
) {
    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        PackageActions.isPackageInstalled(context, packageName)

    fun isPackageEnabled(context: Context, packageName: String): Boolean =
        PackageActions.isPackageEnabled(context, packageName)

    fun getPackageVersion(context: Context, packageName: String): Pair<String, Long>? =
        PackageActions.getPackageVersion(context, packageName)

    fun resolveFrameworkInfo(context: Context): FrameworkInfo? =
        PackageActions.resolveFrameworkInfo(context)

    fun inspectFrameworkIssue(context: Context): FrameworkCompatibilityMonitor.FrameworkIssue? =
        PackageActions.inspectFrameworkIssue(context)

    fun getLsposedModuleVersion(context: Context): String? =
        PackageActions.getLsposedModuleVersion(context)

    fun getLsposedModuleInfo(context: Context): Pair<String, String>? =
        PackageActions.getLsposedModuleInfo(context)

    fun hasRootAccess(): Boolean = PackageActions.hasRootAccess()

    fun isInstalledFromPlay(context: Context): Boolean = PackageActions.isInstalledFromPlay(context)

    fun showAppDetailsInPlayStore(context: Context) =
        PackageActions.showAppDetailsInPlayStore(context, config)

    fun isPlayStoreAvailable(context: Context): Boolean =
        PackageActions.isPlayStoreAvailable(context)

    fun openPlayStoreOrGithub(context: Context): String? =
        PackageActions.openPlayStoreOrGithub(context, config)
}
