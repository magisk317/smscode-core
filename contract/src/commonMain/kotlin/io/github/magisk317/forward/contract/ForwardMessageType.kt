package io.github.magisk317.forward.contract

/** Stable wire vocabulary owned by magisk-forward-core. */
object ForwardMessageType {
    const val SMS = "sms"
    const val APP_NOTIFICATION = "app_notify"
    const val CALL_NOTIFICATION = "call_notify"
    const val BLACKLIST_HIT = "blacklist_hit"
}

object ForwardSource {
    const val SMS_HOOK = "sms_hook"
    const val NOTIFICATION_LISTENER = "nls"
    const val NMS_HOOK = "nms_hook"
    const val TELEPHONY_STATE = "telephony_state"
}

data class ForwardPayload(
    val eventId: String = "",
    val sender: String? = null,
    val body: String? = null,
    val date: Long = 0L,
    val company: String? = null,
    val smsCode: String? = null,
    val packageName: String? = null,
    val notifyChannelId: String = "",
    val messageType: String = ForwardMessageType.SMS,
    val forwardSource: String = "unknown",
    val callType: Int = 0,
    val callStage: String = "",
    val simSlot: Int? = null,
    val subId: Int? = null,
    val appIcon: String = "",
)
