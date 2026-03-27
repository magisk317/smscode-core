package io.github.magisk317.smscode.verification

interface VerificationPrefs {
    fun showNotification(): Boolean
    fun autoCancelNotification(): Boolean
    fun notificationRetentionMs(): Long
    fun autoInputEnabled(): Boolean
    fun autoInputDelayMs(): Long
    fun copyToClipboardEnabled(): Boolean
    fun showToast(): Boolean
    fun recordSmsEnabled(): Boolean
    fun blockSmsEnabled(): Boolean
    fun markAsReadEnabled(): Boolean
    fun deleteSmsEnabled(): Boolean
    fun deduplicateSmsEnabled(): Boolean
}
