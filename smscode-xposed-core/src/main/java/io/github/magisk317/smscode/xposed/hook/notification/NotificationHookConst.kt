package io.github.magisk317.smscode.xposed.hook.notification

object NotificationHookConst {
    private fun resolveNamespace(): String = io.github.magisk317.smscode.xposed.runtime.CoreRuntime.access.actionNamespace

    val ACTION_FORWARD_SMS: String
        get() = "${resolveNamespace()}.ACTION_FORWARD_SMS"

    const val KEY_IPC_TOKEN = "ipc_token"
    const val KEY_FORCE_STOP_RECOVERY = "pref_force_stop_recovery"
    const val KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE = "pref_force_stop_recovery_relaunch_once"

    val ACTION_FORCE_STOP_RECOVERY_WAKEUP: String
        get() = "${resolveNamespace()}.FORCE_STOP_RECOVERY_WAKEUP"
    const val EXTRA_FORCE_STOP_REASON = "reason"
    const val EXTRA_FORCE_STOP_EVENT_ID = "event_id"
}
