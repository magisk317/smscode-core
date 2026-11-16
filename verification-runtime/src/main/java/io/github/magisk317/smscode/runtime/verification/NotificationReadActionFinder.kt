package io.github.magisk317.smscode.runtime.verification

import android.app.Notification
import android.os.Build

/**
 * Locates the "mark as read" action inside a posted notification.
 *
 * Detection order mirrors OTP Helper: prefer the platform semantic action (API 28+), fall back
 * to an English title heuristic for ROMs that never set `semanticAction`. No vendor SMS app in
 * the current device dump corpus attaches the semantic action (MIUI com.android.mms confirmed
 * by jadx: zero SEMANTIC_ACTION_MARK_AS_READ hits), so the heuristic keeps AOSP-family apps
 * covered while vendor support stays with the DB-based path.
 */
object NotificationReadActionFinder {

    /** Sentinel for platforms below P where [Notification.Action.semanticAction] does not exist. */
    const val NO_SEMANTIC_ACTION = -1

    /**
     * Pure decision core, testable without Android types. [sdkInt] defaults to the running
     * platform so callers may pin it for tests.
     *
     * The title heuristic deliberately rejects anything containing "unread": "Mark as unread"
     * contains the "read" substring and clicking it would flip the conversation the wrong way.
     * Localized titles (e.g. "标记为已读") intentionally fall through — the semantic action is
     * the reliable path; the heuristic is only a last resort for English-only fallbacks.
     */
    fun matches(semanticAction: Int, title: CharSequence?, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (sdkInt >= Build.VERSION_CODES.P && semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) {
            return true
        }
        val normalized = title?.toString()?.lowercase() ?: return false
        if (normalized.contains("unread")) return false
        return normalized.contains("mark") && normalized.contains("read")
    }

    /** Resolves a notification's semantic action safely across API levels. */
    fun semanticActionOf(action: Notification.Action, sdkInt: Int = Build.VERSION.SDK_INT): Int =
        if (sdkInt >= Build.VERSION_CODES.P) action.semanticAction else NO_SEMANTIC_ACTION

    /** First mark-as-read action carried by [notification], or null when the app posts none. */
    fun find(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            if (matches(semanticActionOf(action), action.title)) {
                return action
            }
        }
        return null
    }
}
