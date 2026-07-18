package io.github.magisk317.smscode.runtime.common.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.magisk317.smscode.runtime.common.packageenv.PackageEnvCore
import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy

/**
 * Shared package / store / network helpers used by multiple parent apps.
 *
 * Product constants and failure copy come from [PackageActionConfig]; pure package probes
 * delegate to [PackageEnvCore] / [ThirdPartyAppActions].
 */
object PackageActions {

    @JvmStatic
    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        PackageEnvCore.isPackageInstalled(context, packageName)

    @JvmStatic
    fun isPackageEnabled(context: Context, packageName: String): Boolean =
        PackageEnvCore.isPackageEnabled(context, packageName)

    @JvmStatic
    fun getPackageVersion(context: Context, packageName: String): Pair<String, Long>? {
        val info = PackageEnvCore.getPackageVersion(context, packageName) ?: return null
        return info.versionName to info.versionCode
    }

    @JvmStatic
    fun resolveFrameworkInfo(context: Context): FrameworkInfo? =
        PackageEnvCore.resolveFrameworkInfo(context)

    @JvmStatic
    fun inspectFrameworkIssue(context: Context): FrameworkCompatibilityMonitor.FrameworkIssue? =
        PackageEnvCore.inspectFrameworkIssue(context)

    @JvmStatic
    fun getLsposedModuleVersion(context: Context): String? =
        resolveFrameworkInfo(context)?.displayVersion

    @JvmStatic
    fun getLsposedModuleInfo(context: Context): Pair<String, String>? {
        val info = resolveFrameworkInfo(context) ?: return null
        return info.displayName to info.displayVersion
    }

    @JvmStatic
    fun hasRootAccess(): Boolean = PackageEnvCore.hasRootAccess()

    @JvmStatic
    fun joinQQGroup(context: Context, config: PackageActionConfig): String? {
        return if (ThirdPartyAppActions.openViewUri(context, config.qqGroupUrl)) {
            null
        } else {
            context.getString(config.joinQqGroupFailedMessageResId)
        }
    }

    @JvmStatic
    fun isInstalledFromPlay(context: Context): Boolean = try {
        PackageEnvCore.isInstalledFromPlay(context)
    } catch (_: Exception) {
        false
    }

    @JvmStatic
    fun showAppDetailsInPlayStore(context: Context, config: PackageActionConfig) {
        ThirdPartyAppActions.openMarketDetails(context, config.applicationId)
    }

    @JvmStatic
    fun isPlayStoreAvailable(context: Context): Boolean =
        ThirdPartyAppActions.isMarketAvailable(context)

    @JvmStatic
    fun openPlayStoreOrGithub(context: Context, config: PackageActionConfig): String? {
        return when (UpdatePolicy.resolveAvailableTarget(isPlayStoreAvailable(context))) {
            UpdatePolicy.StartupTarget.PLAY -> {
                showAppDetailsInPlayStore(context, config)
                null
            }
            UpdatePolicy.StartupTarget.GITHUB -> BrowserUtils.openWebPage(
                context,
                config.githubLatestReleaseUrl,
                config.browserMissingMessageResId,
            )
        }
    }

    @JvmStatic
    @SuppressLint("MissingPermission")
    fun isOnWifi(context: Context): Boolean {
        return try {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            PackageEnvCore.isOnWifi(context)
        } catch (_: SecurityException) {
            false
        }
    }
}
