package io.github.magisk317.forward.capture

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runNonFatalOrNull

/** Compatibility accessors for PackageManager APIs across Android versions. */
internal object CompatPackageManager {

    @SuppressLint("QueryPermissionsNeeded")
    fun queryBroadcastReceivers(
        packageManager: PackageManager,
        intent: Intent,
    ): List<android.content.pm.ResolveInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(matchAllFlag().toLong()),
            )
        }
        val result = runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "queryBroadcastReceivers",
                Intent::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, intent, matchAllFlag())
        }
        return (result as? List<*>)?.filterIsInstance<android.content.pm.ResolveInfo>().orEmpty()
    }

    fun getPackageInfo(
        packageManager: PackageManager,
        packageName: String,
    ): android.content.pm.PackageInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(matchAllFlag().toLong()),
            )
        }
        return runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, packageName, matchAllFlag()) as? android.content.pm.PackageInfo
        }
    }

    fun getApplicationInfo(
        packageManager: PackageManager,
        packageName: String,
    ): ApplicationInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(matchAllFlag().toLong()),
            )
        }
        return runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "getApplicationInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, packageName, matchAllFlag()) as? ApplicationInfo
        }
    }

    internal fun matchAllFlag(): Int {
        return runNonFatalOrNull {
            PackageManager::class.java
                .getDeclaredField("MATCH_ALL")
                .get(null) as? Int
        } ?: PACKAGE_MANAGER_MATCH_ALL_FALLBACK
    }

    private const val PACKAGE_MANAGER_MATCH_ALL_FALLBACK = 0x00020000
}

