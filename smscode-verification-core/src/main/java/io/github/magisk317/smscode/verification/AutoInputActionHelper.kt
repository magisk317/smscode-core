package io.github.magisk317.smscode.verification

import android.app.ActivityManager
import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.LinkedHashMap

class AutoInputActionHelper<M : SmsMessage>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsMsg: M,
    private val deduplicateEnabled: Boolean? = null,
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
            runCatching {
                val method = ActivityManager::class.java.getMethod("getRunningTasks", Int::class.javaPrimitiveType)
                val result = method.invoke(am, 10) as? List<*>
                result?.filterIsInstance<ActivityManager.RunningTaskInfo>()
            }.getOrNull()
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
        try {
            if (code.isNullOrBlank()) return
            val autoEnter = autoEnterReader(pluginContext)
            val inputIntervalMs = inputIntervalReader(pluginContext)
            val attemptId = attemptRecorder(smsMsg, resolveForegroundPackage())
            inputSender(phoneContext, code, autoEnter, inputIntervalMs, attemptId)
            XLog.d(
                "Auto input request dispatched, autoEnter=%s inputIntervalMs=%d code_len=%d attemptId=%d",
                autoEnter,
                inputIntervalMs,
                code.length,
                attemptId ?: -1L,
            )
        } catch (throwable: Throwable) {
            XLog.e("Error occurs when auto input code", throwable)
        }
    }

    private fun autoInputBlockedHere(): Boolean {
        return try {
            val runningTasks = runningTasksProvider(phoneContext)
            val topPkgPrimary = runningTasks?.firstOrNull()?.topActivity?.packageName
            if (!topPkgPrimary.isNullOrBlank()) {
                XLog.d("topPackagePrimary: %s", topPkgPrimary)
                if (packageBlockedChecker(topPkgPrimary)) {
                    return true
                }
            }

            val appProcesses = runningProcessesProvider(phoneContext) ?: return false
            val topProcess = appProcesses.firstOrNull()?.processName
            val topPackages = appProcesses.firstOrNull()?.pkgList.orEmpty()
            XLog.d("topProcessSecondary: %s, topPackages: %s", topProcess, topPackages.contentToString())

            if (!topProcess.isNullOrBlank() && packageBlockedChecker(topProcess)) {
                return true
            }
            topPackages.any { packageName ->
                packageName.isNotBlank() && packageBlockedChecker(packageName)
            }
        } catch (error: Throwable) {
            XLog.e("", error)
            false
        }
    }

    private fun shouldSkipByRecentAutoInput(): Boolean {
        val key = SmsMessageDedupKeys.buildMessageKey(smsMsg)
        if (key.isBlank()) return false
        val sharedClaim = sharedGateClaimer(
            pluginContext,
            SHARED_AUTO_INPUT_FILE_NAME,
            key,
            AUTO_INPUT_DEDUP_WINDOW_MS,
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
                if (now - entry.value > AUTO_INPUT_DEDUP_WINDOW_MS) {
                    iterator.remove()
                }
            }
            val last = recentAutoInputs[key]
            if (last != null && now - last <= AUTO_INPUT_DEDUP_WINDOW_MS) {
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

    private fun resolveForegroundPackage(): String? {
        val primary = runningTasksProvider(phoneContext)?.firstOrNull()?.topActivity?.packageName
        if (!primary.isNullOrBlank()) return primary
        val processes = runningProcessesProvider(phoneContext)
        val processName = processes?.firstOrNull()?.processName
        if (!processName.isNullOrBlank()) return processName
        return processes?.firstOrNull()?.pkgList?.firstOrNull { it.isNotBlank() }
    }

    private companion object {
        private const val SHARED_AUTO_INPUT_FILE_NAME = "auto_input_dedup"
        private const val AUTO_INPUT_DEDUP_WINDOW_MS = 5_000L
        private const val MAX_AUTO_INPUT_CACHE_SIZE = 128
        private val AUTO_INPUT_CACHE_LOCK = Any()
        private val recentAutoInputs = LinkedHashMap<String, Long>(MAX_AUTO_INPUT_CACHE_SIZE, 0.75f, true)
    }
}
