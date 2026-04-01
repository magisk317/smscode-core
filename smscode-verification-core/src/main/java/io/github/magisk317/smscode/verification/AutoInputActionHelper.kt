package io.github.magisk317.smscode.verification

import android.app.ActivityManager
import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog
import java.lang.reflect.InvocationTargetException
import java.util.LinkedHashMap

class AutoInputActionHelper<M : SmsMessage>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsMsg: M,
    private val deduplicateEnabled: Boolean? = null,
    private val dispatchDelayMs: Long = 0L,
    private val deduplicateReader: (Context) -> Boolean,
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> ClaimResult,
    private val packageBlockedChecker: (String) -> Boolean,
    private val autoEnterReader: (Context) -> Boolean,
    private val inputIntervalReader: (Context) -> Long,
    private val attemptRecorder: (M, String?) -> Long? = { _, _ -> null },
    private val inputSender: (Context, String?, Boolean, Long, Long?) -> Unit,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val runningProcessesProvider: (Context) -> List<ActivityManager.RunningAppProcessInfo>? = { context ->
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        am?.runningAppProcesses
    },
    private val runningTasksProvider: (Context) -> List<ActivityManager.RunningTaskInfo>? = { context ->
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        if (am == null) {
            null
        } else {
            try {
                val method = ActivityManager::class.java.getMethod("getRunningTasks", Int::class.javaPrimitiveType)
                val result = method.invoke(am, 10) as? List<*>
                result?.filterIsInstance<ActivityManager.RunningTaskInfo>()
            } catch (_: NoSuchMethodException) {
                null
            } catch (_: IllegalAccessException) {
                null
            } catch (_: InvocationTargetException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    },
) {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
    )

    fun run() {
        if (isDeduplicateEnabled() && shouldSkipByRecentAutoInput()) {
            return
        }
        val code = smsMsg.smsCode
        if (autoInputBlockedHere()) {
            return
        }
        autoInputCode(code)
    }

    private fun isDeduplicateEnabled(): Boolean {
        return deduplicateEnabled ?: deduplicateReader(pluginContext)
    }

    private fun autoInputCode(code: String?) {
        if (code.isNullOrBlank()) return
        val autoEnter = autoEnterReader(pluginContext)
        val inputIntervalMs = inputIntervalReader(pluginContext)
        val attemptId = attemptRecorder(smsMsg, resolveForegroundPackage())
        try {
            inputSender(phoneContext, code, autoEnter, inputIntervalMs, attemptId)
            XLog.d(
                "Auto input request dispatched, autoEnter=%s inputIntervalMs=%d code_len=%d attemptId=%d",
                autoEnter,
                inputIntervalMs,
                code.length,
                attemptId ?: -1L,
            )
        } catch (error: SecurityException) {
            XLog.e("Error occurs when auto input code", error)
        } catch (error: IllegalArgumentException) {
            XLog.e("Error occurs when auto input code", error)
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when auto input code", error)
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when auto input code", error)
        }
    }

    private fun autoInputBlockedHere(): Boolean {
        val runningTasks = readRunningTasksSafely()
        if (runningTasks != null) {
            val topPkgPrimary = runningTasks.firstOrNull()?.topActivity?.packageName
            if (!topPkgPrimary.isNullOrBlank()) {
                XLog.d("topPackagePrimary: %s", topPkgPrimary)
                if (isBlockedPackageSafely(topPkgPrimary)) {
                    return true
                }
            }
        }

        val appProcesses = readRunningProcessesSafely() ?: return false
        val topProcess = appProcesses.firstOrNull()?.processName
        val topPackages = appProcesses.firstOrNull()?.pkgList.orEmpty()
        XLog.d("topProcessSecondary: %s, topPackages: %s", topProcess, topPackages.contentToString())

        if (!topProcess.isNullOrBlank() && isBlockedPackageSafely(topProcess)) {
            return true
        }
        return topPackages.any { packageName ->
            packageName.isNotBlank() && isBlockedPackageSafely(packageName)
        }
    }

    private fun shouldSkipByRecentAutoInput(): Boolean {
        val key = SmsMessageDedupKeys.buildMessageKey(smsMsg)
        if (key.isBlank()) return false
        val dedupWindowMs = resolveAutoInputDedupWindowMs()
        val sharedClaim = sharedGateClaimer(
            pluginContext,
            SHARED_AUTO_INPUT_FILE_NAME,
            key,
            dedupWindowMs,
            MAX_AUTO_INPUT_CACHE_SIZE,
        )
        if (!sharedClaim.claimed) {
            XLog.w(
                "Diag auto-input dedup skip: key=%s ageMs=%d source=shared_store",
                key,
                sharedClaim.ageMs ?: -1L,
            )
            return true
        }
        val now = currentTimeMillis()
        synchronized(AUTO_INPUT_CACHE_LOCK) {
            val iterator = recentAutoInputs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > dedupWindowMs) {
                    iterator.remove()
                }
            }
            val last = recentAutoInputs[key]
            if (last != null && now - last <= dedupWindowMs) {
                XLog.w(
                    "Diag auto-input dedup skip: key=%s ageMs=%d",
                    key,
                    now - last,
                )
                return true
            }
            recentAutoInputs[key] = now
            while (recentAutoInputs.size > MAX_AUTO_INPUT_CACHE_SIZE) {
                val firstKey = recentAutoInputs.entries.firstOrNull()?.key ?: break
                recentAutoInputs.remove(firstKey)
            }
        }
        return false
    }

    private fun resolveAutoInputDedupWindowMs(): Long {
        return (dispatchDelayMs + AUTO_INPUT_DEDUP_EXTRA_MS)
            .coerceAtLeast(AUTO_INPUT_DEDUP_WINDOW_MS)
    }

    private fun resolveForegroundPackage(): String? {
        val primary = readRunningTasksSafely()?.firstOrNull()?.topActivity?.packageName
        if (!primary.isNullOrBlank()) return primary
        val processes = readRunningProcessesSafely()
        val processName = processes?.firstOrNull()?.processName
        if (!processName.isNullOrBlank()) return processName
        return processes?.firstOrNull()?.pkgList?.firstOrNull { it.isNotBlank() }
    }

    private fun isBlockedPackageSafely(packageName: String): Boolean {
        return try {
            packageBlockedChecker(packageName)
        } catch (error: SecurityException) {
            XLog.e("Error occurs when checking blocked package: %s", packageName, error)
            false
        } catch (error: IllegalArgumentException) {
            XLog.e("Error occurs when checking blocked package: %s", packageName, error)
            false
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when checking blocked package: %s", packageName, error)
            false
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when checking blocked package: %s", packageName, error)
            false
        }
    }

    private fun readRunningTasksSafely(): List<ActivityManager.RunningTaskInfo>? {
        return try {
            runningTasksProvider(phoneContext)
        } catch (error: SecurityException) {
            XLog.e("Error occurs when querying running tasks", error)
            null
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when querying running tasks", error)
            null
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when querying running tasks", error)
            null
        }
    }

    private fun readRunningProcessesSafely(): List<ActivityManager.RunningAppProcessInfo>? {
        return try {
            runningProcessesProvider(phoneContext)
        } catch (error: SecurityException) {
            XLog.e("Error occurs when querying running processes", error)
            null
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when querying running processes", error)
            null
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when querying running processes", error)
            null
        }
    }

    private companion object {
        private const val SHARED_AUTO_INPUT_FILE_NAME = "auto_input_dedup"
        private const val AUTO_INPUT_DEDUP_WINDOW_MS = 5_000L
        private const val AUTO_INPUT_DEDUP_EXTRA_MS = 5_000L
        private const val MAX_AUTO_INPUT_CACHE_SIZE = 128
        private val AUTO_INPUT_CACHE_LOCK = Any()
        private val recentAutoInputs = LinkedHashMap<String, Long>(MAX_AUTO_INPUT_CACHE_SIZE, 0.75f, true)
    }
}
