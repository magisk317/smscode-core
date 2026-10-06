package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * Registers the copy-code receiver that runs when the user taps the notification.
 *
 * Both hosts had their own copy with an identical register/unregister pair, differing
 * only in the action prefix (each uses its own application id) and in how the
 * "show notification" pref is read. The receiver body itself is already shared through
 * [io.github.magisk317.smscode.runtime.verification.CodeNotificationActionHandler].
 */
class CopyCodeReceiverRegistrar(
    private val receiver: BroadcastReceiver,
    private val action: String,
) {
    /** True once [registerMe] succeeded, so a hot reload does not register twice. */
    @Volatile
    private var registered = false

    /**
     * Registers [receiver] for [action] if [shouldRegister] allows it.
     *
     * Registration is a no-op when already done, which happens on a hot module reload.
     */
    fun registerMe(context: Context, shouldRegister: () -> Boolean): Boolean {
        if (registered) return false
        if (!shouldRegister()) return false
        val filter = IntentFilter().apply { addAction(action) }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Not exported: only this module's own notification may trigger it.
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            registered = true
            true
        }.getOrElse {
            XLog.w("Copy code receiver register failed: %s", it.message ?: it.javaClass.simpleName)
            false
        }
    }

    /** Unregisters if needed; safe to call when nothing was registered. */
    fun unregisterMe(context: Context) {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
            .onFailure {
                XLog.w("Copy code receiver unregister failed: %s", it.message ?: it.javaClass.simpleName)
            }
    }
}
