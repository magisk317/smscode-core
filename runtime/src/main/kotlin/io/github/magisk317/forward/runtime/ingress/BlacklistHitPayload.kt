package io.github.magisk317.forward.runtime.ingress

import android.content.Intent

/**
 * Reconstructs a blacklist hit record from the forward broadcast extras.
 * Returns null when the broadcast is not a blacklist hit or lacks identity,
 * so the caller can fall through to the normal forward path.
 */
object BlacklistHitPayload {

    fun fromIntent(intent: Intent): BlacklistHitRecord? {
        val msgType = intent.getStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE)
        if (msgType != ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT) return null
        val eventId = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty()
        val source = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BLACKLIST_SOURCE).orEmpty()
        if (eventId.isBlank() || source.isBlank()) return null
        return BlacklistHitRecord(
            eventId = eventId,
            source = source,
            sender = intent.getStringExtra(ForwardBroadcastContract.EXTRA_SENDER),
            body = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BODY),
            smsDate = intent.getLongExtra(ForwardBroadcastContract.EXTRA_DATE, 0L),
            matchType = intent.getStringExtra(ForwardBroadcastContract.EXTRA_MATCH_TYPE),
            pattern = intent.getStringExtra(ForwardBroadcastContract.EXTRA_PATTERN),
            actionDelete = intent.getBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_DELETE, false),
            actionBlock = intent.getBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_BLOCK, false),
            blockReason = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BLOCK_REASON),
            createdAt = intent.getLongExtra(
                ForwardBroadcastContract.EXTRA_CREATED_AT,
                System.currentTimeMillis(),
            ),
        )
    }
}
