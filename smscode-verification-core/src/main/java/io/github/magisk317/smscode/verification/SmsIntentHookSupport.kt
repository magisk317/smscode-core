package io.github.magisk317.smscode.verification

import android.content.Intent
import android.provider.Telephony
import kotlin.math.abs

object SmsIntentHookSupport {
    private const val EVENT_ID_EXTRA = "event_id"
    private const val DISPATCH_HANDLED_EXTRA_PREFIX = "xsms_dispatch_handled:"
    private const val DEFAULT_HANDLER_KEY = "default"

    fun isSmsAction(action: String?): Boolean {
        return action == Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
            action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
    }

    fun ensureEventId(intent: Intent): String {
        val existing = intent.getStringExtra(EVENT_ID_EXTRA).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = "sms_${System.currentTimeMillis().toString(36)}_${abs(intent.hashCode()).toString(36)}"
        intent.putExtra(EVENT_ID_EXTRA, generated)
        return generated
    }

    fun markDispatchHandled(
        intent: Intent,
        action: String?,
        handlerKey: String = DEFAULT_HANDLER_KEY,
    ): Boolean {
        if (action.isNullOrBlank()) return false
        val normalizedHandlerKey = handlerKey.trim().ifBlank { DEFAULT_HANDLER_KEY }
        val key = "$DISPATCH_HANDLED_EXTRA_PREFIX$normalizedHandlerKey:$action"
        if (intent.getBooleanExtra(key, false)) {
            return true
        }
        intent.putExtra(key, true)
        return false
    }
}
