package io.github.magisk317.forward.contract

import android.content.Intent
import kotlin.math.abs

/** Android transport adapter for the product-neutral forwarding payload. */
object ForwardBroadcastContract {
    const val ACTION_FORWARD = "io.github.magisk317.forward.action.FORWARD"

    /** Action suffix appended to the runtime namespace to form the forward broadcast action. */
    const val ACTION_FORWARD_SUFFIX = "ACTION_FORWARD_SMS"

    /** Action suffix appended to the runtime namespace to form the recovery wake-up action. */
    const val ACTION_RECOVERY_WAKEUP_SUFFIX = "FORCE_STOP_RECOVERY_WAKEUP"

    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_SENDER = "sender"
    const val EXTRA_BODY = "body"
    const val EXTRA_DATE = "date"
    const val EXTRA_COMPANY = "company"
    const val EXTRA_SMS_CODE = "smsCode"
    const val EXTRA_PACKAGE_NAME = "packageName"
    const val EXTRA_NOTIFY_CHANNEL_ID = "notify_channel_id"
    const val EXTRA_MESSAGE_TYPE = "msgType"
    const val EXTRA_FORWARD_SOURCE = "forward_source"
    const val EXTRA_CALL_STAGE = "call_stage"
    const val EXTRA_CALL_TYPE = "call_type"
    const val EXTRA_IPC_TOKEN = "ipc_token"
    const val EXTRA_SIM_SLOT = "sim_slot"
    const val EXTRA_SUB_ID = "sub_id"
    const val EXTRA_APP_ICON = "app_icon"
    const val EXTRA_FORCE_STOP_REASON = "reason"

    /** Forward broadcast action for a module namespace, matching the receiver manifest filter. */
    fun forwardAction(namespace: String): String = "$namespace.$ACTION_FORWARD_SUFFIX"

    /** Recovery wake-up action for a module namespace, matching the recovery service filter. */
    fun recoveryWakeupAction(namespace: String): String = "$namespace.$ACTION_RECOVERY_WAKEUP_SUFFIX"

    fun write(intent: Intent, payload: ForwardPayload, token: String? = null): Intent = intent.apply {
        putExtra(EXTRA_EVENT_ID, payload.eventId)
        putExtra(EXTRA_SENDER, payload.sender)
        putExtra(EXTRA_BODY, payload.body)
        putExtra(EXTRA_DATE, payload.date)
        putExtra(EXTRA_COMPANY, payload.company)
        putExtra(EXTRA_SMS_CODE, payload.smsCode)
        putExtra(EXTRA_PACKAGE_NAME, payload.packageName)
        putExtra(EXTRA_NOTIFY_CHANNEL_ID, payload.notifyChannelId)
        putExtra(EXTRA_MESSAGE_TYPE, payload.messageType)
        putExtra(EXTRA_FORWARD_SOURCE, payload.forwardSource)
        putExtra(EXTRA_CALL_TYPE, payload.callType)
        putExtra(EXTRA_CALL_STAGE, payload.callStage)
        payload.simSlot?.let { putExtra(EXTRA_SIM_SLOT, it) }
        payload.subId?.let { putExtra(EXTRA_SUB_ID, it) }
        if (payload.appIcon.isNotEmpty()) putExtra(EXTRA_APP_ICON, payload.appIcon)
        if (!token.isNullOrBlank()) putExtra(EXTRA_IPC_TOKEN, token)
    }

    fun read(intent: Intent): ForwardPayload = ForwardPayload(
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty(),
        sender = intent.getStringExtra(EXTRA_SENDER),
        body = intent.getStringExtra(EXTRA_BODY),
        date = intent.getLongExtra(EXTRA_DATE, 0L),
        company = intent.getStringExtra(EXTRA_COMPANY),
        smsCode = intent.getStringExtra(EXTRA_SMS_CODE),
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
        notifyChannelId = intent.getStringExtra(EXTRA_NOTIFY_CHANNEL_ID).orEmpty(),
        messageType = intent.getStringExtra(EXTRA_MESSAGE_TYPE) ?: ForwardMessageType.SMS,
        forwardSource = intent.getStringExtra(EXTRA_FORWARD_SOURCE) ?: "unknown",
        callType = intent.getIntExtra(EXTRA_CALL_TYPE, 0),
        callStage = intent.getStringExtra(EXTRA_CALL_STAGE).orEmpty(),
        simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
        subId = intent.getIntExtra(EXTRA_SUB_ID, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
        appIcon = intent.getStringExtra(EXTRA_APP_ICON).orEmpty(),
    )

    fun buildEventId(prefix: String, seed: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = abs((seed + now).hashCode()).toString(36)
        return "${prefix}_${now}_$suffix"
    }
}
