package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.utils.XLog

object AutoInputBroadcastHelper {
    const val EXTRA_IPC_TOKEN = "ipc_token"

    fun sendText(
        context: Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
        actionResolver: () -> String,
        tokenProvider: () -> String = { CorePrefs.getString(EXTRA_IPC_TOKEN, "") },
        broadcaster: (Context, Intent) -> Unit = { senderContext, intent ->
            senderContext.sendOrderedBroadcast(intent, null)
        },
    ) {
        if (text == null) return
        val resolvedAttemptId = attemptId ?: SystemClock.elapsedRealtimeNanos()
        val intent = Intent(actionResolver()).apply {
            putExtra("code", text)
            putExtra("autoEnter", autoEnter)
            putExtra("inputIntervalMs", inputIntervalMs)
            putExtra("attemptId", resolvedAttemptId)
            normalizeIpcToken(tokenProvider())?.let { putExtra(EXTRA_IPC_TOKEN, it) }
        }
        broadcaster(context, intent)
        XLog.i(
            "Dispatched ACTION_AUTO_INPUT request: attemptId=%d code_len=%d autoEnter=%s inputIntervalMs=%d",
            resolvedAttemptId,
            text.length,
            autoEnter,
            inputIntervalMs,
        )
    }

    fun normalizeIpcToken(token: String?): String? = token?.trim()?.takeIf { it.isNotEmpty() }
}
