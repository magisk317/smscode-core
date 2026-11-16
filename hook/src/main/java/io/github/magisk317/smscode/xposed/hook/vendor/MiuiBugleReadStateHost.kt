package io.github.magisk317.smscode.xposed.hook.vendor

import android.content.Context

/**
 * Host capabilities for the shared MIUI bugle read-state hook. Hosts supply their own
 * pref/effect gates and verification-code parser so the shared hook stays policy-free.
 */
interface MiuiBugleReadStateHost {
    val applicationId: String

    fun isModuleEnabled(context: Context): Boolean

    fun isMobileAutomationAllowed(context: Context): Boolean

    fun isMarkAsReadEnabled(context: Context): Boolean

    fun shouldSuppressByRelay(context: Context, tag: String): Boolean

    /** Returns the parsed verification code for [body], or null/blank when none. */
    suspend fun parseVerificationCode(context: Context, body: String): String?
}
