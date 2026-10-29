package io.github.magisk317.forward.capture

import java.util.Locale

/**
 * Single source of truth for "which notification is a call event".
 *
 * The publisher is not always the dialer UI: AOSP dialers post from
 * `com.android.dialer` / `com.android.incallui`, while MIUI/HyperOS Telecom posts the
 * incoming/missed call notifications from `com.android.server.telecom` (channels
 * `TelecomIncomingCalls`, `MiuiTelecomMissedCalls`, ...). The hook and the app-side
 * diagnostics both read these sets so the classification cannot drift between them.
 */
object CallNotificationMatcher {
    /** Package names known to publish call notifications, independent of the ROM. */
    val CALL_SOURCE_PACKAGES: Set<String> = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.incallui",
        "com.android.server.telecom",
    )

    /** Telecom framework channels that only carry call events. */
    val TELECOM_CHANNEL_IDS: Set<String> = setOf(
        "TelecomIncomingCalls",
        "MiuiTelecomMissedCalls",
        "MiuiTelecomMissedCallsForDNDMode",
        "TelecomCallBlocking",
        "TelecomDisconnectedCalls",
    )

    /**
     * True when the package is a dialer/call UI or the Telecom framework itself.
     * Package-name tokens keep vendor dialers working without a release.
     */
    fun isCallSourcePackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        if (normalized in CALL_SOURCE_PACKAGES) return true
        return normalized.contains("dialer") || normalized.contains("incallui")
    }

    fun isTelecomCallChannel(channelId: String): Boolean =
        channelId.trim() in TELECOM_CHANNEL_IDS

    /**
     * True when a notification should be relayed as a call event: the source has to be
     * a call publisher and the content has to look like a call (category call or a call
     * keyword). Channel-only matches still require one of the two content signals so a
     * third-party app cannot claim the Telecom channel name by itself.
     */
    fun isCallNotification(
        packageName: String,
        category: String?,
        channelId: String,
        hasCallKeyword: Boolean,
    ): Boolean {
        val sourceMatches = isCallSourcePackage(packageName) || isTelecomCallChannel(channelId)
        if (!sourceMatches) return false
        return category == CATEGORY_CALL || hasCallKeyword
    }

    /**
     * Broader than [isCallNotification]: used only for diagnostics, where a notification
     * that merely looks like a call is still worth recording as "seen from this package".
     */
    fun looksLikeCall(
        category: String?,
        channelId: String,
        hasCallKeyword: Boolean,
    ): Boolean = category == CATEGORY_CALL || isTelecomCallChannel(channelId) || hasCallKeyword

    const val CATEGORY_CALL: String = "call"
}
