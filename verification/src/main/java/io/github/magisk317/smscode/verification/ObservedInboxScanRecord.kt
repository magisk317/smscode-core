package io.github.magisk317.smscode.verification

data class ObservedInboxScanRecord(
    val smsId: Long,
    val triggerUri: String,
    val sender: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val code: String,
    val simSlot: Int = -1,
    val subId: Int = 0,
)
