package io.github.magisk317.smscode.runtime.common.appentry

import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Shared body for the host manifest `SECRET_CODE` receivers.
 *
 * The receiver class itself must stay in each host (the manifest binds the
 * component by fully-qualified name); only the onReceive logic is shared.
 */
object SecretCodeEntry {

    const val ACTION_SECRET_CODE = "android.provider.Telephony.SECRET_CODE"

    fun onReceive(context: Context, intent: Intent, mainActivityClass: Class<*>) {
        if (intent.action != ACTION_SECRET_CODE) return
        val mainIntent = Intent(context, mainActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(mainIntent)
        MagiskOtel.event(
            name = "app.secret_code",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "reason" to "open_main",
            ),
            statusOk = true,
        )
    }
}
