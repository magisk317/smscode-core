package io.github.magisk317.forward.capture

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.capability.prefs.CorePrefs
import io.github.magisk317.xposed.capability.runtime.CoreRuntime
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runNonFatalCatching

/**
 * Delivers the forward broadcast to the module endpoint and keeps that
 * endpoint alive: force-stop recovery, foreground relaunch and the ordered
 * ack retry used in debug builds all live here.
 */
class ForwardNotificationDispatcher(
    private val recoveryServiceClassName: String?,
    private val relaunchActivityClassName: String?,
) {

    fun dispatch(
        systemContext: Context,
        intent: Intent,
        sourcePackage: String,
        eventId: String,
        endpoint: ForwardDispatchEndpoint,
    ) {
        withClearedCallingIdentity("sendBroadcast:$sourcePackage#$eventId") {
            val recoveryEnabled = isForceStopRecoveryEnabled(sourcePackage)
            val targetUsers = resolveTargetUsers(Binder.getCallingUid())
            if (recoveryEnabled) {
                attemptForceStopRecoveryIfNeeded(
                    systemContext = systemContext,
                    modulePackage = endpoint.packageName,
                    targetUsers = targetUsers,
                    sourcePackage = sourcePackage,
                    eventId = eventId,
                    reason = "pre_dispatch",
                )
            }
            for (userHandle in targetUsers) {
                runNonFatalCatching {
                    sendBroadcastAsUser(
                        systemContext = systemContext,
                        intent = intent,
                        userHandle = userHandle,
                        sourcePackage = sourcePackage,
                        eventId = eventId,
                        endpoint = endpoint,
                        recoveryEnabled = recoveryEnabled,
                    )
                }.onSuccess {
                    return@withClearedCallingIdentity
                }.onFailure { t ->
                    XLog.w(
                        message = TAG + ": sendBroadcastAsUser failed. pkg=%s event=%s user=%s err=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }

            XLog.w(message = TAG + ": fallback sendBroadcast without user. pkg=%s event=%s", sourcePackage, eventId)
            systemContext.sendBroadcast(intent)
        }
    }

    /** IPC token attached to forward broadcasts; empty means receiver-side legacy bypass. */
    fun readIpcToken(): String {
        return readCorePrefString(key = IPC_TOKEN_PREF_KEY, defaultValue = "")
    }

    @SuppressLint("MissingPermission")
    private fun sendBroadcastAsUser(
        systemContext: Context,
        intent: Intent,
        userHandle: UserHandle,
        sourcePackage: String,
        eventId: String,
        endpoint: ForwardDispatchEndpoint,
        recoveryEnabled: Boolean,
    ) {
        val sendIntent = Intent(intent)
        if (CoreRuntime.access.debug) {
            val resultReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, callbackIntent: Intent?) {
                    val ackData = resultData ?: "<null>"
                    XLog.i(
                        message = TAG + ": ordered ack pkg=%s event=%s user=%s resultCode=%d resultData=%s extras=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        resultCode,
                        ackData,
                        getResultExtras(true)?.toString() ?: "<null>",
                    )

                    if (!recoveryEnabled || ackData != "<null>") {
                        return
                    }

                    val relaunchOnceEnabled = isForceStopRecoveryRelaunchOnceEnabled(
                        callerPackageHint = sourcePackage,
                    )
                    val recovered = attemptForceStopRecoveryIfNeeded(
                        systemContext = systemContext,
                        modulePackage = endpoint.packageName,
                        targetUsers = listOf(userHandle),
                        sourcePackage = sourcePackage,
                        eventId = eventId,
                        reason = "ordered_ack_empty",
                        alwaysWake = true,
                    )
                    val relaunched = if (relaunchOnceEnabled) {
                        attemptForegroundRelaunchOnce(
                            systemContext = systemContext,
                            modulePackage = endpoint.packageName,
                            userHandle = userHandle,
                            sourcePackage = sourcePackage,
                            eventId = eventId,
                        )
                    } else {
                        false
                    }
                    if (!recovered && !relaunched) return

                    runCatching {
                        // Best effort retry after process wake-up; avoid ordered callback recursion.
                        systemContext.sendBroadcastAsUser(Intent(sendIntent), userHandle)
                        XLog.w(
                            message = TAG + ": retry broadcast after recovery. pkg=%s event=%s user=%s",
                            sourcePackage,
                            eventId,
                            describeUserHandle(userHandle),
                        )
                    }.onFailure { error ->
                        XLog.w(
                            message = TAG + ": retry after recovery failed. pkg=%s event=%s user=%s err=%s",
                            sourcePackage,
                            eventId,
                            describeUserHandle(userHandle),
                            error.message ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
            systemContext.sendOrderedBroadcastAsUser(
                sendIntent,
                userHandle,
                null,
                resultReceiver,
                null,
                0,
                null,
                null,
            )
        } else {
            systemContext.sendBroadcastAsUser(sendIntent, userHandle)
        }
        XLog.d(
            message = TAG + ": broadcast sent. pkg=%s event=%s user=%s ordered=%s",
            sourcePackage,
            eventId,
            describeUserHandle(userHandle),
            CoreRuntime.access.debug,
        )
    }

    internal fun isForceStopRecoveryEnabled(callerPackageHint: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedForceStopRecoveryEnabled
        if (cached != null && now - cachedForceStopRecoveryCheckedAt <= RECOVERY_PREF_CACHE_MS) {
            return cached
        }
        val enabled = readCorePrefBoolean(
            key = KEY_FORCE_STOP_RECOVERY,
            defaultValue = false,
            callerPackageHint = callerPackageHint,
        )
        cachedForceStopRecoveryEnabled = enabled
        cachedForceStopRecoveryCheckedAt = now
        return enabled
    }

    private fun isForceStopRecoveryRelaunchOnceEnabled(callerPackageHint: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedRecoveryRelaunchOnceEnabled
        if (cached != null && now - cachedRecoveryRelaunchOnceCheckedAt <= RECOVERY_PREF_CACHE_MS) {
            return cached
        }
        val enabled = readCorePrefBoolean(
            key = KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
            defaultValue = false,
            callerPackageHint = callerPackageHint,
        )
        cachedRecoveryRelaunchOnceEnabled = enabled
        cachedRecoveryRelaunchOnceCheckedAt = now
        return enabled
    }

    private fun attemptForceStopRecoveryIfNeeded(
        systemContext: Context,
        modulePackage: String,
        targetUsers: List<UserHandle>,
        sourcePackage: String,
        eventId: String,
        reason: String,
        alwaysWake: Boolean = false,
    ): Boolean {
        if (!shouldAttemptRecoveryNow()) return false
        var recovered = false
        for (userHandle in targetUsers) {
            val userId = resolveUserId(userHandle)
            val stopped = isPackageStopped(systemContext, modulePackage, userId)
            if (!stopped && !alwaysWake) continue
            val unstopped = if (stopped) {
                clearPackageStoppedState(systemContext, modulePackage, userId)
            } else {
                false
            }
            val recoveryToken = readIpcToken()
            val warmedUp = wakeRecoveryService(
                systemContext = systemContext,
                modulePackage = modulePackage,
                userHandle = userHandle,
                reason = reason,
                eventId = eventId,
                token = recoveryToken,
            )
            recovered = recovered || unstopped || warmedUp
            XLog.w(
                message = TAG + ": recovery attempted. module=%s source=%s event=%s user=%s stopped=%s unstopped=%s warmed=%s reason=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
                stopped,
                unstopped,
                warmedUp,
                reason,
            )
        }
        return recovered
    }

    private fun shouldAttemptRecoveryNow(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastRecoveryAttemptAt < RECOVERY_COOLDOWN_MS) {
                return false
            }
            lastRecoveryAttemptAt = now
        }
        return true
    }

    private fun attemptForegroundRelaunchOnce(
        systemContext: Context,
        modulePackage: String,
        userHandle: UserHandle,
        sourcePackage: String,
        eventId: String,
    ): Boolean {
        val activityClassName = relaunchActivityClassName ?: return false
        if (!shouldAttemptForegroundRelaunchNow()) return false
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(modulePackage, activityClassName)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("recovery_from_pkg", sourcePackage)
            putExtra("recovery_event_id", eventId)
        }

        val launched = runCatching {
            HookHelpers.callMethod(systemContext, "startActivityAsUser", launchIntent, userHandle)
            true
        }.getOrElse {
            runCatching {
                systemContext.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        }

        if (launched) {
            XLog.w(
                message = TAG + ": foreground relaunch triggered. module=%s source=%s event=%s user=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
            )
        } else {
            XLog.w(
                message = TAG + ": foreground relaunch failed. module=%s source=%s event=%s user=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
            )
        }
        return launched
    }

    private fun shouldAttemptForegroundRelaunchNow(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastForegroundRelaunchAttemptAt < FOREGROUND_RELAUNCH_COOLDOWN_MS) {
                return false
            }
            lastForegroundRelaunchAttemptAt = now
        }
        return true
    }

    private fun resolveUserId(userHandle: UserHandle): Int {
        return runCatching {
            val method = UserHandle::class.java.getMethod("getIdentifier")
            (method.invoke(userHandle) as? Int) ?: 0
        }.getOrDefault(0)
    }

    private fun isPackageStopped(systemContext: Context, packageName: String, userId: Int): Boolean {
        return runNonFatalCatching {
            val appInfo = getApplicationInfoForUser(systemContext, packageName, userId) ?: return false
            (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }.getOrElse { t ->
            XLog.w(
                message = TAG + ": isPackageStopped failed. pkg=%s user=%d err=%s",
                packageName,
                userId,
                t.message ?: t.javaClass.simpleName,
            )
            false
        }
    }

    private fun getApplicationInfoForUser(systemContext: Context, packageName: String, userId: Int): ApplicationInfo? {
        val pm = systemContext.packageManager
        val asUser = runCatching {
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "getApplicationInfoAsUser" && it.parameterTypes.size == 3
            } ?: return@runCatching null
            method.invoke(pm, packageName, CompatPackageManager.matchAllFlag(), userId) as? ApplicationInfo
        }.getOrNull()
        if (asUser != null) return asUser
        return CompatPackageManager.getApplicationInfo(pm, packageName)
    }

    private fun clearPackageStoppedState(systemContext: Context, packageName: String, userId: Int): Boolean {
        val pm = systemContext.packageManager
        val reflected = runCatching {
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "setPackageStoppedState" && it.parameterTypes.size == 3
            } ?: return@runCatching false
            method.invoke(pm, packageName, false, userId)
            true
        }.getOrDefault(false)
        if (reflected) return true

        val cmd = "cmd package set-stopped-state --user $userId $packageName false"
        return runShellCommand(cmd)
    }

    private fun runShellCommand(command: String): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                XLog.w(message = TAG + ": shell command failed(%d): %s", exitCode, command)
            }
            exitCode == 0
        }.getOrElse {
            XLog.w(
                message = TAG + ": shell command exception: %s err=%s",
                command,
                it.message ?: it.javaClass.simpleName,
            )
            false
        }
    }

    private fun wakeRecoveryService(
        systemContext: Context,
        modulePackage: String,
        userHandle: UserHandle,
        reason: String,
        eventId: String,
        token: String,
    ): Boolean {
        val serviceClassName = recoveryServiceClassName ?: return false
        val wakeIntent = Intent(ForwardBroadcastContract.recoveryWakeupAction(CoreRuntime.access.actionNamespace)).apply {
            setClassName(modulePackage, serviceClassName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(ForwardBroadcastContract.EXTRA_FORCE_STOP_REASON, reason)
            putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, eventId)
            if (token.isBlank()) {
                XLog.w(TAG + ": recovery wake token empty; receiver may use legacy compat bypass")
            } else {
                putExtra(ForwardBroadcastContract.EXTRA_IPC_TOKEN, token)
            }
        }
        val startedAsUser = runCatching {
            HookHelpers.callMethod(systemContext, "startServiceAsUser", wakeIntent, userHandle)
            true
        }.getOrElse { false }
        if (startedAsUser) return true
        return runCatching { systemContext.startService(wakeIntent) != null }.getOrDefault(false)
    }

    private fun resolveTargetUsers(callerUid: Int): List<UserHandle> {
        val result = ArrayList<UserHandle>(2)
        runCatching {
            if (callerUid >= 0) {
                result.add(UserHandle.getUserHandleForUid(callerUid))
            }
        }.onFailure {
            XLog.w(message = TAG + ": resolve caller user failed. callerUid=%d", callerUid)
        }
        runCatching {
            val allUserHandle = UserHandle::class.java.getField("ALL").get(null) as? UserHandle
            if (allUserHandle != null && result.none { it == allUserHandle }) {
                result.add(allUserHandle)
            }
        }.onFailure {
            XLog.w("reading UserHandle.ALL failed: ${it.message}", it)
        }
        if (result.isEmpty()) {
            runCatching {
                val owner = UserHandle::class.java.getField("OWNER").get(null) as? UserHandle
                if (owner != null) result.add(owner)
            }.onFailure {
                XLog.w("reading UserHandle.OWNER failed: ${it.message}", it)
            }
        }
        return result
    }

    private fun describeUserHandle(userHandle: UserHandle): String {
        return runCatching {
            val method = UserHandle::class.java.getMethod("getIdentifier")
            method.invoke(userHandle)?.toString() ?: userHandle.toString()
        }.getOrDefault(userHandle.toString())
    }

    private fun readCorePrefBoolean(
        key: String,
        defaultValue: Boolean,
        callerPackageHint: String? = null,
    ): Boolean {
        return runNonFatalCatching {
            CorePrefs.getBoolean(key, defaultValue)
        }.getOrElse { t ->
            XLog.w(
                message = TAG + ": CorePrefs boolean failed. key=%s err=%s caller=%s",
                key,
                t.message ?: t.javaClass.simpleName,
                callerPackageHint ?: "<none>",
            )
            defaultValue
        }
    }

    private fun readCorePrefString(
        key: String,
        defaultValue: String,
        callerPackageHint: String? = null,
    ): String {
        return runNonFatalCatching {
            CorePrefs.getString(key, defaultValue)
        }.getOrElse { t ->
            XLog.w(
                message = TAG + ": CorePrefs string failed. key=%s err=%s caller=%s",
                key,
                t.message ?: t.javaClass.simpleName,
                callerPackageHint ?: "<none>",
            )
            defaultValue
        }
    }

    @Volatile
    private var cachedForceStopRecoveryEnabled: Boolean? = null

    @Volatile
    private var cachedForceStopRecoveryCheckedAt: Long = 0L

    @Volatile
    private var lastRecoveryAttemptAt: Long = 0L

    @Volatile
    private var cachedRecoveryRelaunchOnceEnabled: Boolean? = null

    @Volatile
    private var cachedRecoveryRelaunchOnceCheckedAt: Long = 0L

    @Volatile
    private var lastForegroundRelaunchAttemptAt: Long = 0L

    companion object {
        const val TAG = "ForwardNotificationDispatcher"
        const val IPC_TOKEN_PREF_KEY = "ipc_token"
        const val KEY_FORCE_STOP_RECOVERY = "pref_force_stop_recovery"
        const val KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE = "pref_force_stop_recovery_relaunch_once"
        private const val RECOVERY_COOLDOWN_MS = 3000L
        private const val FOREGROUND_RELAUNCH_COOLDOWN_MS = 60_000L
        private const val RECOVERY_PREF_CACHE_MS = 30_000L
    }
}

/** Runs [block] with the system-server calling identity cleared, restoring it afterwards. */
internal inline fun <T> withClearedCallingIdentity(reason: String, block: () -> T): T {
    val callerUid = Binder.getCallingUid()
    val callerPid = Binder.getCallingPid()
    if (callerUid != Process.SYSTEM_UID) {
        XLog.d(
            message = "clearCallingIdentity reason=%s callerUid=%d callerPid=%d",
            reason,
            callerUid,
            callerPid,
        )
    }
    val token = Binder.clearCallingIdentity()
    return try {
        block()
    } finally {
        Binder.restoreCallingIdentity(token)
    }
}
