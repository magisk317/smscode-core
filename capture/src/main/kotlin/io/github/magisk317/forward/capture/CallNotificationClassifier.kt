package io.github.magisk317.forward.capture

import io.github.magisk317.forward.contract.constant.ForwardCallType
import java.util.Locale

/**
 * Call-domain classifier for captured notifications.
 *
 * Owns the call vocabulary (keywords, stages, types) and the phone-number
 * heuristics. Anything that is not a call is returned as null so the product
 * can fall through to its other routes.
 */
object CallNotificationClassifier {

    /** Result of classifying a notification as a call. */
    data class CallClassification(
        val callType: Int,
        val label: String,
        val stage: String,
    )

    const val DEFAULT_CALL_LABEL = "通话通知"

    /**
     * Returns a classification when the notification looks like a call,
     * otherwise null.
     */
    fun classify(
        packageName: String,
        category: String?,
        channelId: String,
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
    ): CallClassification? {
        val normalizedText = normalizedHintText(title, body, tickerText, expandedText)
        val isCallCategory = category == CATEGORY_CALL
        val callSourceMatched = CallNotificationMatcher.isCallSourcePackage(packageName) ||
            CallNotificationMatcher.isTelecomCallChannel(channelId)
        val hasCallKeyword = containsAnyKeyword(normalizedText, CALL_NOTIFY_KEYWORDS)
        val isCallNotify = CallNotificationMatcher.isCallNotification(
            packageName = packageName,
            category = category,
            channelId = channelId,
            hasCallKeyword = hasCallKeyword,
        )
        if (!isCallNotify) return null

        var callType = resolveCallType(normalizedText)
        if (callType == ForwardCallType.UNKNOWN && callSourceMatched && isCallCategory) {
            callType = ForwardCallType.INCOMING
        }
        val callStage = resolveCallStage(
            callType = callType,
            isCallCategory = isCallCategory || hasCallKeyword,
            normalizedText = normalizedText,
            notifyChannelId = channelId,
        )
        return CallClassification(
            callType = callType,
            label = callTypeLabel(callType),
            stage = callStage,
        )
    }

    /** Diagnostics helper: does this notification come from a call source at all. */
    fun looksLikeCallSource(
        packageName: String,
        category: String?,
        channelId: String,
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
    ): Boolean {
        val normalizedText = normalizedHintText(title, body, tickerText, expandedText)
        val hasCallKeyword = containsAnyKeyword(normalizedText, CALL_NOTIFY_KEYWORDS)
        return CallNotificationMatcher.looksLikeCall(category, channelId, hasCallKeyword)
    }

    /**
     * Sender shown to the user for a call notification: prefer a phone number
     * when the title is blank, generic or free of digits.
     */
    fun resolveDisplaySender(
        title: String,
        body: String,
        expandedText: String,
        tickerText: String,
    ): String {
        val normalizedTitle = title.trim()
        val number = extractPhoneNumber(title, body, expandedText, tickerText)
        if (number.isBlank()) return title
        if (normalizedTitle.isBlank() || isGenericCallTitle(normalizedTitle) || normalizedTitle.none { it.isDigit() }) {
            return number
        }
        return title
    }

    /** Lowercased concatenation of every text view, used as the classification hint. */
    fun normalizedHintText(
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
    ): String {
        return buildString {
            append(title)
            append('\n')
            append(body)
            append('\n')
            append(tickerText)
            if (expandedText.isNotBlank()) {
                append('\n')
                append(expandedText)
            }
        }.lowercase(Locale.ROOT)
    }

    private fun resolveCallStage(
        callType: Int,
        isCallCategory: Boolean,
        normalizedText: String,
        notifyChannelId: String,
    ): String {
        return when (callType) {
            ForwardCallType.MISSED,
            ForwardCallType.REJECTED,
            ForwardCallType.BLOCKED,
            ForwardCallType.VOICEMAIL,
            -> "ended"
            ForwardCallType.OUTGOING -> "dialing"
            ForwardCallType.INCOMING -> "ringing"
            else -> {
                if (isOngoingCallChannel(notifyChannelId) || containsAnyKeyword(normalizedText, ONGOING_CALL_KEYWORDS)) {
                    "ongoing"
                } else if (isCallCategory) {
                    "ongoing"
                } else {
                    ""
                }
            }
        }
    }

    private fun extractPhoneNumber(
        title: String,
        body: String,
        expandedText: String,
        tickerText: String,
    ): String {
        val combined = buildString {
            append(title)
            append('\n')
            append(body)
            append('\n')
            append(expandedText)
            append('\n')
            append(tickerText)
        }
        val matcher = PHONE_CANDIDATE_REGEX.findAll(combined)
        var best = ""
        for (match in matcher) {
            val normalized = normalizeDigits(match.value)
            if (normalized.length < 6) continue
            if (normalized.length > best.length) {
                best = normalized
            }
        }
        return best
    }

    private fun normalizeDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (
            digits.startsWith(MAINLAND_CHINA_COUNTRY_CODE) &&
            digits.length > MAINLAND_CHINA_MOBILE_LENGTH
        ) {
            digits.removePrefix(MAINLAND_CHINA_COUNTRY_CODE)
        } else {
            digits
        }
    }

    private fun isGenericCallTitle(rawTitle: String): Boolean {
        val normalized = rawTitle.lowercase(Locale.ROOT).replace("\\s+".toRegex(), " ").trim()
        if (normalized.isBlank()) return true
        return GENERIC_CALL_TITLES.any { normalized.contains(it) }
    }

    private fun isOngoingCallChannel(notifyChannelId: String): Boolean {
        val normalized = notifyChannelId.lowercase(Locale.ROOT)
        return normalized.contains("ongoing") || normalized.contains("incall") || normalized.contains("in_call")
    }

    private fun containsAnyKeyword(text: String, keywords: Set<String>): Boolean {
        if (text.isBlank()) return false
        return keywords.any { keyword -> text.contains(keyword) }
    }

    private fun resolveCallType(normalizedText: String): Int {
        return when {
            containsAnyKeyword(normalizedText, VOICEMAIL_KEYWORDS) -> ForwardCallType.VOICEMAIL
            containsAnyKeyword(normalizedText, MISSED_CALL_KEYWORDS) -> ForwardCallType.MISSED
            containsAnyKeyword(normalizedText, REJECTED_CALL_KEYWORDS) -> ForwardCallType.REJECTED
            containsAnyKeyword(normalizedText, BLOCKED_CALL_KEYWORDS) -> ForwardCallType.BLOCKED
            containsAnyKeyword(normalizedText, OUTGOING_CALL_KEYWORDS) -> ForwardCallType.OUTGOING
            containsAnyKeyword(normalizedText, INCOMING_CALL_KEYWORDS) -> ForwardCallType.INCOMING
            else -> ForwardCallType.UNKNOWN
        }
    }

    private fun callTypeLabel(callType: Int): String {
        return when (callType) {
            ForwardCallType.INCOMING -> "来电"
            ForwardCallType.OUTGOING -> "去电"
            ForwardCallType.MISSED -> "未接"
            ForwardCallType.VOICEMAIL -> "语音信箱"
            ForwardCallType.REJECTED -> "拒接"
            ForwardCallType.BLOCKED -> "拦截"
            else -> DEFAULT_CALL_LABEL
        }
    }

    private const val CATEGORY_CALL = "call"
    private const val MAINLAND_CHINA_COUNTRY_CODE = "86"
    private const val MAINLAND_CHINA_MOBILE_LENGTH = 11

    private val MISSED_CALL_KEYWORDS = setOf(
        "未接",
        "missed call",
        "missed",
        "未接来电",
    )
    private val VOICEMAIL_KEYWORDS = setOf(
        "语音信箱",
        "语音邮箱",
        "语音邮件",
        "語音信箱",
        "語音郵箱",
        "語音郵件",
        "voicemail",
        "voice mail",
    )
    private val INCOMING_CALL_KEYWORDS = setOf(
        "来电",
        "incoming call",
        "incoming",
    )
    private val OUTGOING_CALL_KEYWORDS = setOf(
        "去电",
        "outgoing call",
        "outgoing",
        "dialed",
        "已拨电话",
    )
    private val ONGOING_CALL_KEYWORDS = setOf(
        "通话中",
        "正在通话",
        "ongoing call",
        "in call",
    )
    private val REJECTED_CALL_KEYWORDS = setOf(
        "拒接",
        "已拒接",
        "rejected",
        "declined",
    )
    private val BLOCKED_CALL_KEYWORDS = setOf(
        "拦截",
        "已拦截",
        "blocked call",
        "spam blocked",
    )
    private val CALL_NOTIFY_KEYWORDS = buildSet {
        addAll(MISSED_CALL_KEYWORDS)
        addAll(VOICEMAIL_KEYWORDS)
        addAll(INCOMING_CALL_KEYWORDS)
        addAll(OUTGOING_CALL_KEYWORDS)
        addAll(REJECTED_CALL_KEYWORDS)
        addAll(BLOCKED_CALL_KEYWORDS)
        add("来电")
        add("通话")
        add("电话")
        add("call")
    }
    private val GENERIC_CALL_TITLES = setOf(
        "未接电话",
        "未接来电",
        "未接",
        "missed call",
        "missed",
        "来电提醒",
        "来电",
        "incoming call",
        "incoming",
        "通话",
        "电话",
        "去电",
        "outgoing call",
        "outgoing",
        "通话中",
        "ongoing call",
    )
    private val PHONE_CANDIDATE_REGEX = Regex("(\\+?\\d[\\d\\s\\-]{4,}\\d)")
}
