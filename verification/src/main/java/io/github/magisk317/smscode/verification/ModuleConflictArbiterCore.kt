package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.smscode.xposed.utils.XLog

class ModuleConflictArbiterCore(
    private val targetPackage: String,
    private val allowConflictBypass: () -> Boolean,
    private val packageInstalledChecker: (Context, String) -> Boolean = ::isPackageInstalled,
) {
    @Volatile
    private var checked = false

    @Volatile
    private var suppressedByRelay = false

    @Volatile
    private var decisionLogged = false

    fun shouldSuppress(context: Context?, source: String): Boolean {
        if (context == null) return false
        if (allowConflictBypass()) {
            logBypassFlagOnce(source)
            return false
        }
        if (!checked) {
            synchronized(this) {
                if (!checked) {
                    suppressedByRelay = packageInstalledChecker(context, targetPackage)
                    checked = true
                }
            }
        }
        logDecisionOnce(source)
        return suppressedByRelay
    }

    private fun logBypassFlagOnce(source: String) {
        if (decisionLogged) return
        synchronized(this) {
            if (decisionLogged) return
            XLog.w(
                "Conflict arbiter bypassed: reason=%s source=%s package=%s",
                BYPASS_REASON_BUILD_FLAG,
                source,
                targetPackage,
            )
            decisionLogged = true
        }
    }

    private fun logDecisionOnce(source: String) {
        if (decisionLogged) return
        synchronized(this) {
            if (decisionLogged) return
            if (suppressedByRelay) {
                XLog.w(
                    "Conflict arbiter active: reason=%s source=%s package=%s",
                    SUPPRESSION_REASON,
                    source,
                    targetPackage,
                )
            } else {
                XLog.i(
                    "Conflict arbiter bypassed: source=%s package=%s",
                    source,
                    targetPackage,
                )
            }
            decisionLogged = true
        }
    }

    companion object {
        const val SUPPRESSION_REASON = "suppressed_by_relay_package"
        const val BYPASS_REASON_BUILD_FLAG = "allow_conflict_bypass"

        fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(packageName, 0)
                }
                true
            }.getOrDefault(false)
        }
    }
}
