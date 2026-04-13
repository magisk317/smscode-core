package io.github.magisk317.smscode.runtime.common.packageenv

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.smscode.runtime.common.utils.FrameworkInfo
import io.github.magisk317.smscode.runtime.common.utils.FrameworkInfoResolver

enum class PackageState {
    NOT_INSTALLED,
    DISABLED,
    ENABLED,
}

data class PackageVersionInfo(
    val versionName: String,
    val versionCode: Long,
)

object PackageEnvCore {

    private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
    private const val HOOKER_ANNOTATION_ERROR_TEXT = "Hooker should be annotated with @XposedHooker"

    @JvmStatic
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            getPackageInfoCompat(pm, packageName)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val appInfo = getApplicationInfoCompat(pm, packageName)
            appInfo.enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getPackageVersion(context: Context, packageName: String): PackageVersionInfo? {
        val pm = context.packageManager
        return try {
            val packageInfo = getPackageInfoCompat(pm, packageName)
            PackageVersionInfo(
                versionName = packageInfo.versionName ?: "",
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    @JvmStatic
    fun getPackageState(context: Context, packageName: String): PackageState =
        when {
            isPackageEnabled(context, packageName) -> PackageState.ENABLED
            isPackageInstalled(context, packageName) -> PackageState.DISABLED
            else -> PackageState.NOT_INSTALLED
        }

    @JvmStatic
    fun isInstalledFromPlay(context: Context): Boolean = try {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE_PACKAGE_NAME
    } catch (_: Exception) {
        false
    }

    @JvmStatic
    fun isOnWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @JvmStatic
    fun hasRootAccess(): Boolean {
        val uid = runSuCommand("id -u")?.trim()
        return uid == "0"
    }

    @JvmStatic
    fun resolveFrameworkInfo(context: Context): FrameworkInfo? {
        val installedInfo = FrameworkInfoResolver.resolveInstalledModuleInfo()
        if (installedInfo != null) return installedInfo
        val snapshot = ActivationDiagnosticsStore.snapshot(context)
        return FrameworkInfoResolver.resolveFromServiceSnapshot(
            frameworkName = snapshot.lastServiceFrameworkName,
            frameworkVersion = snapshot.lastServiceFrameworkVersion,
        )
    }

    @JvmStatic
    fun inspectFrameworkIssue(context: Context): FrameworkCompatibilityMonitor.FrameworkIssue? {
        val frameworkInfo = resolveFrameworkInfo(context)
        val now = System.currentTimeMillis()
        val bootStartAt = (now - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val matched = RuntimeLogStore.query(minutes = null, keyword = null, limit = 2000)
            .lastOrNull { entry ->
                entry.timestamp >= bootStartAt &&
                    entry.message.contains(HOOKER_ANNOTATION_ERROR_TEXT)
            }
        return FrameworkCompatibilityMonitor.detectIssue(
            frameworkInfo = frameworkInfo,
            latestLogMessage = matched?.message,
            detectedAt = matched?.timestamp ?: now,
        )
    }

    private fun runSuCommand(command: String): String? = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) output else null
    } catch (_: Exception) {
        null
    }

    private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }

    private fun getApplicationInfoCompat(pm: PackageManager, packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
}
