package io.github.magisk317.smscode.verification

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.contract.sim.SmsRoutingMetadata
import kotlinx.coroutines.runBlocking

class ObservedInboxScanner(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIdTracker: SmsInboxSeenTracker,
    private val smsCodeParser: suspend (Context, String) -> String,
    private val inboxRowLoader: (Long, Long?) -> List<InboxRow> = { cutoff, triggeredSmsId ->
        loadRecentInboxRows(phoneContext, cutoff, triggeredSmsId)
    },
) {
    data class InboxRow(
        val smsId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val read: Boolean,
        val simSlot: Int = -1,
        val subId: Int = 0,
    )

    fun scan(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        return scanRows(triggerUri, recentSmsWindowMs, requireCode = true)
    }

    fun scanRouting(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        return scanRows(triggerUri, recentSmsWindowMs, requireCode = false)
    }

    private fun scanRows(
        triggerUri: String,
        recentSmsWindowMs: Long,
        requireCode: Boolean,
    ): List<ObservedInboxScanRecord> {
        val cutoff = System.currentTimeMillis() - recentSmsWindowMs
        val resolvedTriggerUri = triggerUri.ifBlank { DEFAULT_SMS_TRIGGER_URI }
        val triggeredSmsId = parseTriggeredSmsId(triggerUri)
        return runCatching {
            inboxRowLoader(cutoff, triggeredSmsId).mapNotNull { row ->
                if (!smsIdTracker.markSeen(row.smsId)) {
                    return@mapNotNull null
                }
                val code = if (requireCode) {
                    runBlocking { smsCodeParser(pluginContext, row.body) }.orEmpty()
                } else {
                    ""
                }
                if (requireCode && code.isBlank()) {
                    return@mapNotNull null
                }
                ObservedInboxScanRecord(
                    smsId = row.smsId,
                    triggerUri = resolvedTriggerUri,
                    sender = row.sender,
                    body = row.body,
                    date = row.date,
                    read = row.read,
                    code = code,
                    simSlot = row.simSlot,
                    subId = row.subId,
                )
            }
        }.getOrElse {
            XLog.w("SmsInboxObserver scan failed: %s", it.message ?: it.javaClass.simpleName)
            emptyList()
        }
    }

    companion object {
        private const val DEFAULT_SMS_TRIGGER_URI = "content://sms"
        private const val MAX_RECENT_SMS_COUNT = 32
        private val TRIGGERED_SMS_ID_REGEX = Regex("""^content://sms(?:/[^/?#]+)*/(\d+)(?:[?#].*)?$""")

        private fun loadRecentInboxRows(
            phoneContext: Context,
            cutoff: Long,
            triggeredSmsId: Long?,
        ): List<InboxRow> {
            val baseProjection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            )
            val selection: String
            val selectionArgs: Array<String>
            val sortOrder: String?
            if (triggeredSmsId != null) {
                selection = "${Telephony.Sms._ID}=? AND ${Telephony.Sms.TYPE}=?"
                selectionArgs = arrayOf(triggeredSmsId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
                sortOrder = null
            } else {
                selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
                selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
                sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"
            }
            val rows = mutableListOf<InboxRow>()
            queryInboxRows(
                phoneContext = phoneContext,
                projection = null,
                fallbackProjection = baseProjection,
                selection = selection,
                selectionArgs = selectionArgs,
                sortOrder = sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val rawSimSlot = cursor.firstOptionalInt(SIM_SLOT_COLUMNS)
                    val rawSubId = cursor.firstOptionalInt(SUB_ID_COLUMNS)
                    val routing = SmsRoutingMetadata(
                        simSlot = rawSimSlot,
                        subId = rawSubId,
                    ).normalized(::resolveSlotIndex)
                    XLog.log(
                        Log.DEBUG,
                        LogRoute.SMS_HOOK,
                        false,
                        false,
                        "Diag SMS routing provider row: sms_id=%d rawSlot=%d rawSubId=%d simSlot=%d subId=%d",
                        smsId,
                        rawSimSlot ?: -1,
                        rawSubId ?: 0,
                        routing.simSlot ?: SmsRoutingMetadata.UNKNOWN_SIM_SLOT,
                        routing.subId ?: SmsRoutingMetadata.UNKNOWN_SUB_ID,
                    )
                    rows += InboxRow(
                        smsId = smsId,
                        sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty(),
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
                        date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) != 0,
                        simSlot = routing.simSlot ?: SmsRoutingMetadata.UNKNOWN_SIM_SLOT,
                        subId = routing.subId ?: SmsRoutingMetadata.UNKNOWN_SUB_ID,
                    )
                }
            }
            return rows
        }

        private fun queryInboxRows(
            phoneContext: Context,
            projection: Array<String>?,
            fallbackProjection: Array<String>,
            selection: String,
            selectionArgs: Array<String>,
            sortOrder: String?,
        ): Cursor? {
            return runCatching {
                phoneContext.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder,
                )
            }.getOrNull() ?: phoneContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                fallbackProjection,
                selection,
                selectionArgs,
                sortOrder,
            )
        }

        private fun Cursor.firstOptionalInt(columns: Array<String>): Int? {
            for (column in columns) {
                val index = getColumnIndex(column)
                if (index >= 0 && !isNull(index)) {
                    return getInt(index)
                }
            }
            return null
        }

        private fun parseTriggeredSmsId(triggerUri: String): Long? {
            if (triggerUri.isBlank()) return null
            val parsedId = runCatching { Uri.parse(triggerUri) }.getOrNull()
                ?.takeIf { it.scheme == "content" && it.authority == "sms" }
                ?.lastPathSegment
                ?.toLongOrNull()
            return parsedId
                ?: TRIGGERED_SMS_ID_REGEX.find(triggerUri)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
        }

        private fun resolveSlotIndex(subId: Int): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
            return runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
        }

        private val SIM_SLOT_COLUMNS = arrayOf("sim_slot", "slot", "slot_id", "phone_id", "phoneId", "simSlot")
        private val SUB_ID_COLUMNS = arrayOf("sub_id", "sim_id", "simId", "subscription", "subscription_id")
    }
}
