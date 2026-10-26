package io.github.magisk317.smscode.runtime.contract.record

import io.github.magisk317.smscode.runtime.contract.sim.SmsRoutingMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of [SmsMsgRecord] as persisted by code-record export files.
 *
 * Field names and defaults mirror the SmsMsg entity's serialization surface exactly, so
 * record files written by earlier app versions keep parsing after hosts rebind the file
 * store to this type.
 */
@Serializable
data class SmsMsgRecordData(
    @SerialName("id") override val id: Long? = null,
    @SerialName("sender") override val sender: String? = null,
    @SerialName("body") override val body: String? = null,
    @SerialName("date") override val date: Long = 0L,
    @SerialName("processedTime") override val processedTime: Long = 0L,
    @SerialName("company") override val company: String? = null,
    @SerialName("code") override val smsCode: String? = null,
    @SerialName("packageName") override val packageName: String? = null,
    @SerialName("notifyChannelId") override val notifyChannelId: String = "",
    @SerialName("simSlot") override val simSlot: Int = SmsRoutingMetadata.UNKNOWN_SIM_SLOT,
    @SerialName("subId") override val subId: Int = SmsRoutingMetadata.UNKNOWN_SUB_ID,
    @SerialName("contactName") val contactName: String = "",
    @SerialName("phoneArea") val phoneArea: String = "",
    @SerialName("forwardStatus") override val forwardStatus: Int = FORWARD_STATUS_NONE,
    @SerialName("forwardTarget") override val forwardTarget: String? = null,
    @SerialName("forwardMessage") override val forwardMessage: String? = null,
    @SerialName("forwardTime") override val forwardTime: Long = 0L,
    @SerialName("msgType") override val msgType: Int = MSG_TYPE_SMS,
    @SerialName("callType") override val callType: Int = 0,
    @SerialName("sessionKey") val sessionKey: String = "",
) : SmsMsgRecord {
    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val MSG_TYPE_SMS = 0
    }
}
