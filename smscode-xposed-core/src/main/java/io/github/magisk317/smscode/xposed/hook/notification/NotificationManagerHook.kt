package io.github.magisk317.smscode.xposed.hook.notification

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
import android.os.UserHandle
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.utils.NotificationLogSanitizer
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.utils.resolveStaticIntFieldOrDefault
import io.github.magisk317.smscode.xposed.utils.runNonFatalCatching
import io.github.magisk317.smscode.xposed.utils.runNonFatalOrNull
import java.util.Locale

class NotificationManagerHook : BaseHook() {
    private data class ModuleEndpoint(
        val packageName: String,
        val prefAuthority: String,
    )

    @Volatile
    private var cachedEndpoint: ModuleEndpoint? = null

    @Volatile
    private var cachedIpcToken: String? = null

    @Volatile
    private var cachedForceStopRecoveryEnabled: Boolean? = null

    @Volatile
    private var cachedForceStopRecoveryCheckedAt: Long = 0L

    @Volatile
    private var lastRecoveryAttemptAt: Long = 0L

    @Volatile
    private var cachedRecoveryRelaunchOnceEnabled: Boolean? = null

    @Volatile
    private var cachedRecoveryRelaunchOnceCheckedAt: Long = 0L

    @Volatile
    private var lastForegroundRelaunchAttemptAt: Long = 0L

    private data class NotifyRoute(
        val msgType: String,
        val smsCode: String? = null,
        val callType: Int = CALL_TYPE_UNKNOWN,
        val callTypeLabel: String = "",
        val callStage: String = "",
    )

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: LoadParam) {
        val isSystemPackage = lpparam.packageName == "android" || lpparam.packageName == "system"
        val isSystemProcess = lpparam.processName == "system" ||
            lpparam.processName == "android" ||
            lpparam.processName == "system_server"
        if (!isSystemPackage || !isSystemProcess) return

        runNonFatalCatching {
            val nmsClass = HookHelpers.findClass("com.android.server.notification.NotificationManagerService", lpparam.classLoader)

            val methods = nmsClass.declaredMethods.filter { it.name == "enqueueNotificationInternal" }
            if (methods.isEmpty()) {
                XLog.w("NotificationManagerHook: no enqueueNotificationInternal method found")
                return
            }

            methods.forEach { method ->
                HookBridge.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            runNonFatalCatching {
                                handleEnqueueNotificationInternal(param)
                            }.onFailure { t ->
                                XLog.e("NotificationManagerHook error", t)
                            }
                        }
                    },
                )
            }
            XLog.w("NotificationManagerHook: successfully hooked enqueueNotificationInternal")
        }.onFailure { t ->
            XLog.e("NotificationManagerHook: failed to hook NotificationManagerService", t)
        }
    }

    private fun handleEnqueueNotificationInternal(param: MethodHookParam) {
        var pkg: String? = null
        var notification: Notification? = null

        for (arg in param.args) {
            if (arg is String && pkg == null) {
                pkg = arg
            } else if (arg is Notification) {
                notification = arg
            }
        }

        if (pkg.isNullOrEmpty() || notification == null) {
            XLog.d(
                "NotificationManagerHook: skip invalid args. pkg=%s hasNotification=%s",
                pkg ?: "<null>",
                notification != null,
            )
            return
        }

        val systemContext = runNonFatalOrNull {
            HookHelpers.callMethod(param.thisObject, "getContext") as? Context
        }

        if (systemContext == null) {
            XLog.w("NotificationManagerHook: failed to get Context")
            return
        }

        val endpoint = getModuleEndpoint(systemContext) ?: run {
            XLog.w("NotificationManagerHook: failed to resolve module endpoint")
            return
        }
        val modulePackage = endpoint.packageName

        if (pkg == modulePackage) {
            XLog.d("NotificationManagerHook: skip self notification. pkg=%s", pkg)
            return
        }

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val tickerText = notification.tickerText?.toString() ?: ""
        val expandedText = resolveExpandedText(notification)
        val notifyChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.channelId.orEmpty()
        } else {
            ""
        }

        val body = resolvePreferredBody(text, expandedText, tickerText)
        if (title.isBlank() && body.isBlank()) {
            if (isWechatPackage(pkg)) {
                XLog.w(
                    "NotificationManagerHook: wechat blank content dropped. flags=0x%s category=%s",
                    Integer.toHexString(notification.flags),
                    notification.category ?: "<null>",
                )
            }
            XLog.d("NotificationManagerHook: skip blank content. pkg=%s", pkg)
            return
        }
        val notifyRoute = resolveNotifyRoute(
            packageName = pkg,
            notification = notification,
            title = title,
            body = body,
            tickerText = tickerText,
            expandedText = expandedText,
            notifyChannelId = notifyChannelId,
        )
        val skipReason = getSkipReason(
            packageName = pkg,
            notification = notification,
            notifyRoute = notifyRoute,
            title = title,
            body = body,
            notifyChannelId = notifyChannelId,
        )
        if (skipReason != null) {
            XLog.d(
                "NotificationManagerHook: skip by policy. pkg=%s reason=%s channel=%s flags=0x%s category=%s",
                pkg,
                skipReason,
                notifyChannelId.ifBlank { "<empty>" },
                Integer.toHexString(notification.flags),
                notification.category ?: "<null>",
            )
            return
        }
        val eventId = buildEventId(pkg)
        XLog.w(
            "Diag nms_hook entry: event_id=%s pkg=%s route=%s",
            eventId,
            pkg,
            notifyRoute.msgType,
        )
        val forwardIntent = Intent(NotificationHookConst.ACTION_FORWARD_SMS)
        forwardIntent.setClassName(modulePackage, FORWARD_RECEIVER_CLASS_NAME)
        forwardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        forwardIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val displaySender = resolveCallSender(title, body, expandedText, tickerText, notifyRoute.msgType)
        forwardIntent.putExtra("sender", displaySender)
        forwardIntent.putExtra("body", body)
        forwardIntent.putExtra("date", System.currentTimeMillis())
        forwardIntent.putExtra("packageName", pkg)
        forwardIntent.putExtra("notify_channel_id", notifyChannelId)
        forwardIntent.putExtra("msgType", notifyRoute.msgType)
        notifyRoute.smsCode?.takeIf { it.isNotBlank() }?.let { forwardIntent.putExtra("smsCode", it) }
        if (notifyRoute.msgType == MSG_TYPE_CALL_NOTIFY) {
            forwardIntent.putExtra("call_type", notifyRoute.callType)
            if (notifyRoute.callStage.isNotBlank()) {
                forwardIntent.putExtra("call_stage", notifyRoute.callStage)
            }
        }
        forwardIntent.putExtra("forward_source", "nms_hook")
        forwardIntent.putExtra("event_id", eventId)

        val pm = systemContext.packageManager
        val appName = runNonFatalCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
        val companyLabel = when (notifyRoute.msgType) {
            MSG_TYPE_CALL_NOTIFY -> notifyRoute.callTypeLabel.ifBlank { CALL_TYPE_LABEL_DEFAULT }
            MSG_TYPE_SMS -> resolveSmsCompanyLabel(
                title = title,
                body = body,
                expandedText = expandedText,
                tickerText = tickerText,
                fallbackLabel = appName,
            )
            else -> appName
        }
        forwardIntent.putExtra("company", companyLabel)

        val token = resolveIpcToken(
            systemContext = systemContext,
            endpoint = endpoint,
            callerPackageHint = pkg,
        )
        if (token.isBlank()) {
            XLog.w(
                "NotificationManagerHook: ipc token empty, continue forwarding with receiver-side bypass. pkg=%s",
                pkg,
            )
        } else {
            forwardIntent.putExtra("ipc_token", token)
        }
        val sensitiveDebugLog = isSensitiveDebugLogEnabled(
            systemContext = systemContext,
            endpoint = endpoint,
            callerPackageHint = pkg,
        )

        XLog.i(
            "NotificationManagerHook intercepted: pkg=%s event=%s type=%s callType=%d title=%s body=%s",
            pkg,
            eventId,
            notifyRoute.msgType,
            notifyRoute.callType,
            NotificationLogSanitizer.formatTitle(title, sensitiveDebugLog),
            NotificationLogSanitizer.formatBody(body, sensitiveDebugLog),
        )
        dispatchForwardBroadcast(systemContext, forwardIntent, pkg, eventId, endpoint)
    }

    private fun resolveNotifyRoute(
        packageName: String,
        notification: Notification,
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
        notifyChannelId: String,
    ): NotifyRoute {
        val normalizedText = normalizeCallHintText(title, body, tickerText, expandedText)
        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        val isDialerPackage = isDialerPackage(packageName)
        val hasCallKeyword = containsAnyKeyword(normalizedText, CALL_NOTIFY_KEYWORDS)
        val isCallNotify = (isDialerPackage && isCallCategory) || (isDialerPackage && hasCallKeyword)
        if (isCallNotify) {
            var callType = resolveCallType(normalizedText)
            if (callType == CALL_TYPE_UNKNOWN && isDialerPackage && isCallCategory) {
                callType = CALL_TYPE_INCOMING
            }
            val callStage = resolveCallStage(
                callType = callType,
                isCallCategory = isCallCategory || hasCallKeyword,
                normalizedText = normalizedText,
                notifyChannelId = notifyChannelId,
            )
            return NotifyRoute(
                msgType = MSG_TYPE_CALL_NOTIFY,
                callType = callType,
                callTypeLabel = callTypeLabel(callType),
                callStage = callStage,
            )
        }
        return resolveTelephonySmsRoute(
            packageName = packageName,
            title = title,
            body = body,
            tickerText = tickerText,
            expandedText = expandedText,
        ) ?: NotifyRoute(msgType = MSG_TYPE_APP_NOTIFY)
    }

    private fun normalizeCallHintText(
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

    private fun buildNotificationContent(
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
    ): String {
        return listOf(title, body, expandedText, tickerText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
    }

    private fun resolveTelephonySmsRoute(
        packageName: String,
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
    ): NotifyRoute? {
        if (!isTelephonySmsPackage(packageName)) return null
        val content = buildNotificationContent(title, body, tickerText, expandedText)
        if (content.isBlank()) return null
        val smsCode = extractLikelyVerificationCode(content)
        if (smsCode.isBlank()) return null
        return NotifyRoute(
            msgType = MSG_TYPE_SMS,
            smsCode = smsCode,
        )
    }

    private fun resolveExpandedText(notification: Notification): String {
        val extras = notification.extras
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            .orEmpty()
        return listOf(bigText, lines, subText).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun resolvePreferredBody(
        text: String,
        expandedText: String,
        tickerText: String,
    ): String {
        val normalizedText = text.trim()
        val normalizedExpanded = expandedText.trim()
        val normalizedTicker = tickerText.trim()
        return when {
            shouldPreferExpandedText(normalizedText, normalizedExpanded) -> normalizedExpanded
            normalizedText.isNotEmpty() -> normalizedText
            normalizedExpanded.isNotEmpty() -> normalizedExpanded
            else -> normalizedTicker
        }
    }

    private fun shouldPreferExpandedText(
        text: String,
        expandedText: String,
    ): Boolean {
        if (expandedText.isBlank()) return false
        if (text.isBlank()) return true
        if (looksTruncated(text) && expandedText.length >= text.length) return true
        val comparableText = stripEdgeEllipsis(text)
        if (comparableText.isNotEmpty() && expandedText.contains(comparableText) && expandedText.length > comparableText.length) {
            return true
        }
        return expandedText.length >= text.length + 8
    }

    private fun looksTruncated(text: String): Boolean {
        val normalized = text.trim()
        return normalized.startsWith("...") ||
            normalized.startsWith("…") ||
            normalized.endsWith("...") ||
            normalized.endsWith("…")
    }

    private fun stripEdgeEllipsis(text: String): String {
        return text.trim()
            .removePrefix("...")
            .removePrefix("…")
            .removeSuffix("...")
            .removeSuffix("…")
            .trim()
    }

    private fun resolveCallStage(
        callType: Int,
        isCallCategory: Boolean,
        normalizedText: String,
        notifyChannelId: String,
    ): String {
        return when (callType) {
            CALL_TYPE_MISSED,
            CALL_TYPE_REJECTED,
            CALL_TYPE_BLOCKED,
            CALL_TYPE_VOICEMAIL,
            -> "ended"
            CALL_TYPE_OUTGOING -> "dialing"
            CALL_TYPE_INCOMING -> "ringing"
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

    private fun resolveCallSender(
        title: String,
        body: String,
        expandedText: String,
        tickerText: String,
        msgType: String,
    ): String {
        if (msgType != MSG_TYPE_CALL_NOTIFY) return title
        val normalizedTitle = title.trim()
        val number = extractPhoneNumber(title, body, expandedText, tickerText)
        if (number.isBlank()) return title
        if (normalizedTitle.isBlank() || isGenericCallTitle(normalizedTitle) || normalizedTitle.none { it.isDigit() }) {
            return number
        }
        return title
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

    private fun isDialerPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        if (normalized in CALL_NOTIFY_PACKAGE_ALLOWLIST) return true
        return normalized.contains("dialer") || normalized.contains("incallui")
    }

    private fun isTelephonySmsPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        if (normalized in TELEPHONY_SMS_PACKAGE_ALLOWLIST) return true
        return normalized.contains("telephony")
    }

    private fun containsAnyKeyword(text: String, keywords: Set<String>): Boolean {
        if (text.isBlank()) return false
        return keywords.any { keyword -> text.contains(keyword) }
    }

    private fun resolveCallType(normalizedText: String): Int {
        return when {
            containsAnyKeyword(normalizedText, VOICEMAIL_KEYWORDS) -> CALL_TYPE_VOICEMAIL
            containsAnyKeyword(normalizedText, MISSED_CALL_KEYWORDS) -> CALL_TYPE_MISSED
            containsAnyKeyword(normalizedText, REJECTED_CALL_KEYWORDS) -> CALL_TYPE_REJECTED
            containsAnyKeyword(normalizedText, BLOCKED_CALL_KEYWORDS) -> CALL_TYPE_BLOCKED
            containsAnyKeyword(normalizedText, OUTGOING_CALL_KEYWORDS) -> CALL_TYPE_OUTGOING
            containsAnyKeyword(normalizedText, INCOMING_CALL_KEYWORDS) -> CALL_TYPE_INCOMING
            else -> CALL_TYPE_UNKNOWN
        }
    }

    private fun callTypeLabel(callType: Int): String {
        return when (callType) {
            CALL_TYPE_INCOMING -> "来电"
            CALL_TYPE_OUTGOING -> "去电"
            CALL_TYPE_MISSED -> "未接"
            CALL_TYPE_VOICEMAIL -> "语音信箱"
            CALL_TYPE_REJECTED -> "拒接"
            CALL_TYPE_BLOCKED -> "拦截"
            else -> CALL_TYPE_LABEL_DEFAULT
        }
    }

    private fun getSkipReason(
        packageName: String,
        notification: Notification,
        notifyRoute: NotifyRoute,
        title: String,
        body: String,
        notifyChannelId: String,
    ): String? {
        val flags = notification.flags
        val isCallNotify = notifyRoute.msgType == MSG_TYPE_CALL_NOTIFY
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 && !isCallNotify) {
            return "foreground_service"
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 && !isCallNotify) {
            return "ongoing_event"
        }
        if (notification.category == Notification.CATEGORY_SERVICE) {
            return "category_service"
        }
        if (!isCallNotify) {
            getFixedNotificationChannelReason(notifyChannelId)?.let { return it }
            getFixedNotificationContentReason(title = title, body = body)?.let { return it }
        }
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            if (ENABLE_WECHAT_GROUP_SUMMARY_BYPASS && isWechatPackage(packageName)) {
                XLog.w(
                    "NotificationManagerHook: bypass group_summary policy for wechat. flags=0x%s category=%s",
                    Integer.toHexString(flags),
                    notification.category ?: "<null>",
                )
                return null
            }
            return "group_summary"
        }
        return null
    }

    private fun getFixedNotificationChannelReason(notifyChannelId: String): String? {
        val normalizedChannel = notifyChannelId.trim().lowercase(Locale.ROOT)
        if (normalizedChannel.isBlank()) return null
        if (normalizedChannel == "voicemail" || normalizedChannel == "voice_mail") {
            return "channel_voicemail"
        }
        if (normalizedChannel.contains("foreground_service") || normalizedChannel.contains("foregroundservice")) {
            return "channel_foreground_service"
        }
        if (normalizedChannel.contains("fgs")) {
            return "channel_fgs"
        }
        if (normalizedChannel.contains("low_importance_service")) {
            return "channel_low_importance_service"
        }
        if (normalizedChannel.contains("service_channel") || normalizedChannel.contains("servicechannel")) {
            return "channel_service"
        }
        if (normalizedChannel.contains(".hide") || normalizedChannel.endsWith("_hide") || normalizedChannel.contains("_hide_")) {
            return "channel_hidden"
        }
        if (normalizedChannel.contains("silent")) {
            return "channel_silent"
        }
        return null
    }

    private fun getFixedNotificationContentReason(
        title: String,
        body: String,
    ): String? {
        val normalizedText = buildString {
            append(title.trim())
            append('\n')
            append(body.trim())
        }.lowercase(Locale.ROOT)
        if (normalizedText.isBlank()) return null
        if (normalizedText.contains("正在运行")) {
            return "content_running"
        }
        if (normalizedText.contains("前台服务") || normalizedText.contains("foreground service")) {
            return "content_foreground_service"
        }
        if (normalizedText.contains("正在检查应用更新") || normalizedText.contains("checking app updates")) {
            return "content_checking_updates"
        }
        return null
    }

    private fun isWechatPackage(packageName: String): Boolean = packageName == WECHAT_PACKAGE

    private fun resolveSmsCompanyLabel(
        title: String,
        body: String,
        expandedText: String,
        tickerText: String,
        fallbackLabel: String,
    ): String {
        val content = buildNotificationContent(title, body, tickerText, expandedText)
        val company = extractCompanyCandidates(content).firstOrNull().orEmpty().trim()
        if (company.isNotBlank()) return company
        return title.trim().takeIf { it.isNotBlank() } ?: fallbackLabel
    }

    private fun extractCompanyCandidates(content: String): List<String> {
        return COMPANY_PATTERN.findAll(content)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun extractLikelyVerificationCode(content: String): String {
        val normalized = content.lowercase(Locale.ROOT)
        val keywordPositions = extractVerificationKeywordPositions(normalized)
        if (keywordPositions.isEmpty()) return ""
        val candidates = VERIFICATION_CODE_PATTERN.findAll(content)
            .map { it.value }
            .filterNot { isLikelyDateTimeToken(it, content) }
            .filterNot { isLikelyUrlToken(it, content) }
            .toList()
        if (candidates.isEmpty()) return ""
        return candidates.sortedWith(
            compareByDescending<String> { verificationCodePriority(it) }
                .thenBy { distanceToVerificationKeyword(keywordPositions, normalized, it.lowercase(Locale.ROOT)) },
        ).first()
    }

    private fun verificationCodePriority(candidate: String): Int = when {
        candidate.matches(Regex("^\\d{6}$")) -> 4
        candidate.matches(Regex("^\\d{4}$")) -> 3
        candidate.matches(Regex("^\\d{4,8}$")) -> 2
        else -> 1
    }

    private fun distanceToVerificationKeyword(
        keywordPositions: List<Int>,
        normalizedContent: String,
        candidate: String,
    ): Int {
        val candidateIndex = normalizedContent.indexOf(candidate)
        if (candidateIndex < 0) return normalizedContent.length
        return keywordPositions.minOfOrNull { kotlin.math.abs(it - candidateIndex) } ?: normalizedContent.length
    }

    private fun isLikelyDateTimeToken(candidate: String, content: String): Boolean {
        if (!candidate.all(Char::isDigit)) return false
        val candidateIndex = content.indexOf(candidate)
        if (candidateIndex < 0) return false
        val prev = content.getOrNull(candidateIndex - 1)
        val next = content.getOrNull(candidateIndex + candidate.length)
        return prev in DATE_TIME_UNIT_TOKENS || next in DATE_TIME_UNIT_TOKENS
    }

    private fun isLikelyUrlToken(candidate: String, content: String): Boolean {
        val candidateIndex = content.indexOf(candidate)
        if (candidateIndex < 0) return false
        val prev = content.getOrNull(candidateIndex - 1)
        val next = content.getOrNull(candidateIndex + candidate.length)
        if (prev in URL_CONTEXT_TOKENS || next in URL_CONTEXT_TOKENS) {
            return true
        }
        val contextStart = (candidateIndex - 8).coerceAtLeast(0)
        val contextEnd = (candidateIndex + candidate.length + 8).coerceAtMost(content.length)
        val nearby = content.substring(contextStart, contextEnd).lowercase(Locale.ROOT)
        return nearby.contains("http://") || nearby.contains("https://") || nearby.contains("www.")
    }

    private fun extractVerificationKeywordPositions(normalizedText: String): List<Int> {
        if (normalizedText.isBlank()) return emptyList()
        val positions = mutableListOf<Int>()
        CJK_VERIFICATION_KEYWORDS.forEach { keyword ->
            var searchStart = 0
            while (searchStart < normalizedText.length) {
                val index = normalizedText.indexOf(keyword, startIndex = searchStart)
                if (index < 0) break
                positions += index
                searchStart = index + keyword.length
            }
        }
        LATIN_VERIFICATION_KEYWORD_PATTERNS.forEach { regex ->
            regex.findAll(normalizedText).forEach { match ->
                positions += match.range.first
            }
        }
        return positions.sorted()
    }

    private fun getModuleEndpoint(systemContext: Context): ModuleEndpoint? {
        cachedEndpoint?.let { return it }
        val resolved = withClearedCallingIdentity("resolveModuleEndpoint") {
            resolveModuleEndpoint(systemContext)
        } ?: return null
        cachedEndpoint = resolved
        XLog.d("NotificationManagerHook: cache endpoint package=%s", resolved.packageName)
        return resolved
    }

    private fun resolveModuleEndpoint(systemContext: Context): ModuleEndpoint? {
        val candidates = LinkedHashSet<String>()
        resolveForwardReceiverPackages(systemContext).forEach { candidates.add(it) }
        CoreRuntime.access.applicationId.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        candidates.add("io.github.magisk317.relay")
        var fallbackCandidate: String? = null

        for (packageName in candidates) {
            val prefAuthority = "$packageName.pref.provider"
            runNonFatalCatching {
                val prefProviderPackage = resolveProviderPackage(systemContext, prefAuthority)
                if (prefProviderPackage == packageName) {
                    return ModuleEndpoint(
                        packageName = packageName,
                        prefAuthority = prefAuthority,
                    )
                }
                if (fallbackCandidate == null && isPackageInstalled(systemContext, packageName)) {
                    fallbackCandidate = packageName
                }
                XLog.w(
                    "NotificationManagerHook: candidate %s rejected. prefProvider=%s",
                    packageName,
                    prefProviderPackage ?: "<null>",
                )
            }.onFailure { t ->
                XLog.w(
                    "NotificationManagerHook: candidate %s resolve failed: %s",
                    packageName,
                    t.message ?: t.javaClass.simpleName,
                )
            }
        }
        if (!fallbackCandidate.isNullOrBlank()) {
            XLog.w(
                "NotificationManagerHook: using unverified endpoint fallback package=%s",
                fallbackCandidate,
            )
            return ModuleEndpoint(
                packageName = fallbackCandidate,
                prefAuthority = "$fallbackCandidate.pref.provider",
            )
        }
        return null
    }

    private fun resolveForwardReceiverPackages(systemContext: Context): List<String> {
        val packages = LinkedHashSet<String>()
        return runNonFatalCatching {
            val intent = Intent(NotificationHookConst.ACTION_FORWARD_SMS)
            val receivers = queryBroadcastReceiversCompat(systemContext.packageManager, intent)
            receivers.forEach { resolveInfo ->
                resolveInfo.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let { packages.add(it) }
            }
            packages.toList()
        }.getOrElse { t ->
            XLog.w(
                "NotificationManagerHook: queryBroadcastReceivers failed: %s",
                t.message ?: t.javaClass.simpleName,
            )
            emptyList()
        }
    }

    private fun resolveProviderPackage(systemContext: Context, authority: String): String? {
        return runNonFatalCatching {
            val info = resolveContentProviderCompat(systemContext.packageManager, authority)
            info?.packageName
        }.getOrElse { t ->
            XLog.w(
                "NotificationManagerHook: resolveContentProvider(%s) failed: %s",
                authority,
                t.message ?: t.javaClass.simpleName,
            )
            null
        }
    }

    private fun queryPrefString(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        key: String,
        defaultValue: String,
        callerPackageHint: String? = null,
    ): String = queryPrefStringValue(
        systemContext = systemContext,
        modulePackageName = endpoint.packageName,
        authority = endpoint.prefAuthority,
        typePath = "string",
        key = key,
        defaultValue = defaultValue,
        callerPackageHint = callerPackageHint,
    )

    private fun queryPrefBoolean(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        key: String,
        defaultValue: Boolean,
        callerPackageHint: String? = null,
    ): Boolean = queryPrefBooleanValue(
        systemContext = systemContext,
        modulePackageName = endpoint.packageName,
        authority = endpoint.prefAuthority,
        typePath = "bool",
        key = key,
        defaultValue = defaultValue,
        callerPackageHint = callerPackageHint,
    )

    private fun resolveIpcToken(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        callerPackageHint: String? = null,
    ): String {
        val freshToken = queryPrefString(
            systemContext = systemContext,
            endpoint = endpoint,
            key = NotificationHookConst.KEY_IPC_TOKEN,
            defaultValue = "",
            callerPackageHint = callerPackageHint,
        )
        if (freshToken.isNotBlank()) {
            if (cachedIpcToken != freshToken) {
                XLog.d("NotificationManagerHook: ipc token refreshed from provider")
            }
            cachedIpcToken = freshToken
            return freshToken
        }

        val cached = cachedIpcToken
        if (!cached.isNullOrBlank()) {
            XLog.w(
                "NotificationManagerHook: ipc token provider unavailable, using cached token. caller=%s",
                callerPackageHint ?: "<none>",
            )
            return cached
        }
        return ""
    }

    private fun isSensitiveDebugLogEnabled(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        callerPackageHint: String? = null,
    ): Boolean {
        if (!CoreRuntime.access.debug) return false
        return queryPrefBoolean(
            systemContext = systemContext,
            endpoint = endpoint,
            key = NotificationHookConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
            defaultValue = false,
            callerPackageHint = callerPackageHint,
        )
    }

    private fun queryPrefBooleanValue(
        systemContext: Context,
        modulePackageName: String,
        authority: String,
        typePath: String,
        key: String,
        defaultValue: Boolean,
        callerPackageHint: String? = null,
    ): Boolean {
        val defaultString = if (defaultValue) "1" else "0"
        val value = queryPrefStringValue(
            systemContext = systemContext,
            modulePackageName = modulePackageName,
            authority = authority,
            typePath = typePath,
            key = key,
            defaultValue = defaultString,
            callerPackageHint = callerPackageHint,
        )
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun queryPrefStringValue(
        systemContext: Context,
        modulePackageName: String,
        authority: String,
        typePath: String,
        key: String,
        defaultValue: String,
        callerPackageHint: String? = null,
    ): String {
        val uri = Uri.parse("content://$authority/$typePath")
            .buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("default", defaultValue)
            .build()
        val primary = withClearedCallingIdentity("queryPref:$authority/$key") {
            queryPrefStringInternal(systemContext, uri, defaultValue)
        }
        if (primary != null) return primary

        val fallbackContext = tryCreatePackageContext(systemContext, modulePackageName)
        if (fallbackContext != null) {
            val fallback = withClearedCallingIdentity("queryPrefFallback:$authority/$key") {
                queryPrefStringInternal(fallbackContext, uri, defaultValue)
            }
            if (fallback != null) {
                XLog.d(
                    "NotificationManagerHook: query pref fallback succeeded. authority=%s key=%s module=%s caller=%s",
                    authority,
                    key,
                    modulePackageName,
                    callerPackageHint ?: "<none>",
                )
                return fallback
            }
        }
        XLog.w(
            "NotificationManagerHook: query pref unavailable. authority=%s key=%s module=%s caller=%s",
            authority,
            key,
            modulePackageName,
            callerPackageHint ?: "<none>",
        )
        return defaultValue
    }

    private fun queryPrefStringInternal(context: Context, uri: Uri, defaultValue: String): String? {
        return runNonFatalCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: defaultValue
                } else {
                    defaultValue
                }
            } ?: run {
                XLog.w("NotificationManagerHook: pref query returned null cursor. uri=%s", uri)
                null
            }
        }.getOrElse { t ->
            XLog.w(
                "NotificationManagerHook: pref query failed. uri=%s err=%s callerUid=%d selfUid=%d",
                uri,
                t.message ?: t.javaClass.simpleName,
                Binder.getCallingUid(),
                Process.myUid(),
            )
            null
        }
    }

    private fun tryCreatePackageContext(systemContext: Context, packageName: String): Context? {
        return runNonFatalOrNull {
            systemContext.createPackageContext(packageName, contextIgnoreSecurityFlag())
        }
    }

    private fun isPackageInstalled(systemContext: Context, packageName: String): Boolean {
        return runNonFatalCatching {
            getPackageInfoCompat(systemContext.packageManager, packageName)
            true
        }.getOrDefault(false)
    }

    private inline fun <T> withClearedCallingIdentity(reason: String, block: () -> T): T {
        val callerUid = Binder.getCallingUid()
        val callerPid = Binder.getCallingPid()
        if (callerUid != Process.SYSTEM_UID) {
            XLog.d(
                "NotificationManagerHook: clearCallingIdentity reason=%s callerUid=%d callerPid=%d",
                reason,
                callerUid,
                callerPid,
            )
        }
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun dispatchForwardBroadcast(
        systemContext: Context,
        intent: Intent,
        sourcePackage: String,
        eventId: String,
        endpoint: ModuleEndpoint,
    ) {
        withClearedCallingIdentity("sendBroadcast:$sourcePackage#$eventId") {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                systemContext.sendBroadcast(intent)
                return@withClearedCallingIdentity
            }

            val recoveryEnabled = isForceStopRecoveryEnabled(systemContext, endpoint, sourcePackage)
            val targetUsers = resolveTargetUsers(Binder.getCallingUid())
            if (recoveryEnabled) {
                attemptForceStopRecoveryIfNeeded(
                    systemContext = systemContext,
                    modulePackage = endpoint.packageName,
                    targetUsers = targetUsers,
                    sourcePackage = sourcePackage,
                    eventId = eventId,
                    reason = "pre_dispatch",
                )
            }
            for (userHandle in targetUsers) {
                runNonFatalCatching {
                    sendBroadcastAsUser(
                        systemContext = systemContext,
                        intent = intent,
                        userHandle = userHandle,
                        sourcePackage = sourcePackage,
                        eventId = eventId,
                        endpoint = endpoint,
                        recoveryEnabled = recoveryEnabled,
                    )
                }.onSuccess {
                    return@withClearedCallingIdentity
                }.onFailure { t ->
                    XLog.w(
                        "NotificationManagerHook: sendBroadcastAsUser failed. pkg=%s event=%s user=%s err=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }

            XLog.w(
                "NotificationManagerHook: fallback sendBroadcast without user. pkg=%s event=%s",
                sourcePackage,
                eventId,
            )
            systemContext.sendBroadcast(intent)
        }
    }

    private fun sendBroadcastAsUser(
        systemContext: Context,
        intent: Intent,
        userHandle: UserHandle,
        sourcePackage: String,
        eventId: String,
        endpoint: ModuleEndpoint,
        recoveryEnabled: Boolean,
    ) {
        val sendIntent = Intent(intent)
        if (CoreRuntime.access.debug) {
            val resultReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, callbackIntent: Intent?) {
                    val ackData = resultData ?: "<null>"
                    XLog.i(
                        "NotificationManagerHook: ordered ack pkg=%s event=%s user=%s resultCode=%d resultData=%s extras=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        resultCode,
                        ackData,
                        getResultExtras(true)?.toString() ?: "<null>",
                    )

                    if (!recoveryEnabled || ackData != "<null>") {
                        return
                    }

                    val relaunchOnceEnabled = isForceStopRecoveryRelaunchOnceEnabled(
                        systemContext = systemContext,
                        endpoint = endpoint,
                        callerPackageHint = sourcePackage,
                    )
                    val recovered = attemptForceStopRecoveryIfNeeded(
                        systemContext = systemContext,
                        modulePackage = endpoint.packageName,
                        targetUsers = listOf(userHandle),
                        sourcePackage = sourcePackage,
                        eventId = eventId,
                        reason = "ordered_ack_empty",
                        alwaysWake = true,
                    )
                    val relaunched = if (relaunchOnceEnabled) {
                        attemptForegroundRelaunchOnce(
                            systemContext = systemContext,
                            modulePackage = endpoint.packageName,
                            userHandle = userHandle,
                            sourcePackage = sourcePackage,
                            eventId = eventId,
                        )
                    } else {
                        false
                    }
                    if (!recovered && !relaunched) return

                    runCatching {
                        // Best effort retry after process wake-up; avoid ordered callback recursion.
                        systemContext.sendBroadcastAsUser(Intent(sendIntent), userHandle)
                        XLog.w(
                            "NotificationManagerHook: retry broadcast after recovery. pkg=%s event=%s user=%s",
                            sourcePackage,
                            eventId,
                            describeUserHandle(userHandle),
                        )
                    }.onFailure { error ->
                        XLog.w(
                            "NotificationManagerHook: retry after recovery failed. pkg=%s event=%s user=%s err=%s",
                            sourcePackage,
                            eventId,
                            describeUserHandle(userHandle),
                            error.message ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
            systemContext.sendOrderedBroadcastAsUser(
                sendIntent,
                userHandle,
                null,
                resultReceiver,
                null,
                0,
                null,
                null,
            )
        } else {
            systemContext.sendBroadcastAsUser(sendIntent, userHandle)
        }
        XLog.d(
            "NotificationManagerHook: broadcast sent. pkg=%s event=%s user=%s ordered=%s",
            sourcePackage,
            eventId,
            describeUserHandle(userHandle),
            CoreRuntime.access.debug,
        )
    }

    private fun isForceStopRecoveryEnabled(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        callerPackageHint: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedForceStopRecoveryEnabled
        if (cached != null && now - cachedForceStopRecoveryCheckedAt <= RECOVERY_PREF_CACHE_MS) {
            return cached
        }
        val enabled = queryPrefBoolean(
            systemContext = systemContext,
            endpoint = endpoint,
            key = NotificationHookConst.KEY_FORCE_STOP_RECOVERY,
            defaultValue = false,
            callerPackageHint = callerPackageHint,
        )
        cachedForceStopRecoveryEnabled = enabled
        cachedForceStopRecoveryCheckedAt = now
        return enabled
    }

    private fun isForceStopRecoveryRelaunchOnceEnabled(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        callerPackageHint: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedRecoveryRelaunchOnceEnabled
        if (cached != null && now - cachedRecoveryRelaunchOnceCheckedAt <= RECOVERY_PREF_CACHE_MS) {
            return cached
        }
        val enabled = queryPrefBoolean(
            systemContext = systemContext,
            endpoint = endpoint,
            key = NotificationHookConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
            defaultValue = false,
            callerPackageHint = callerPackageHint,
        )
        cachedRecoveryRelaunchOnceEnabled = enabled
        cachedRecoveryRelaunchOnceCheckedAt = now
        return enabled
    }

    private fun attemptForceStopRecoveryIfNeeded(
        systemContext: Context,
        modulePackage: String,
        targetUsers: List<UserHandle>,
        sourcePackage: String,
        eventId: String,
        reason: String,
        alwaysWake: Boolean = false,
    ): Boolean {
        if (!shouldAttemptRecoveryNow()) return false
        var recovered = false
        for (userHandle in targetUsers) {
            val userId = resolveUserId(userHandle)
            val stopped = isPackageStopped(systemContext, modulePackage, userId)
            if (!stopped && !alwaysWake) continue
            val unstopped = if (stopped) {
                clearPackageStoppedState(systemContext, modulePackage, userId)
            } else {
                false
            }
            val warmedUp = wakeRecoveryService(systemContext, modulePackage, userHandle, reason, eventId)
            recovered = recovered || unstopped || warmedUp
            XLog.w(
                "NotificationManagerHook: recovery attempted. module=%s source=%s event=%s user=%s stopped=%s unstopped=%s warmed=%s reason=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
                stopped,
                unstopped,
                warmedUp,
                reason,
            )
        }
        return recovered
    }

    private fun shouldAttemptRecoveryNow(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastRecoveryAttemptAt < RECOVERY_COOLDOWN_MS) {
                return false
            }
            lastRecoveryAttemptAt = now
        }
        return true
    }

    private fun attemptForegroundRelaunchOnce(
        systemContext: Context,
        modulePackage: String,
        userHandle: UserHandle,
        sourcePackage: String,
        eventId: String,
    ): Boolean {
        if (!shouldAttemptForegroundRelaunchNow()) return false
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(modulePackage, MAIN_ACTIVITY_CLASS_NAME)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("recovery_from_pkg", sourcePackage)
            putExtra("recovery_event_id", eventId)
        }

        val launched = runCatching {
            HookHelpers.callMethod(systemContext, "startActivityAsUser", launchIntent, userHandle)
            true
        }.getOrElse {
            runCatching {
                systemContext.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        }

        if (launched) {
            XLog.w(
                "NotificationManagerHook: foreground relaunch triggered. module=%s source=%s event=%s user=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
            )
        } else {
            XLog.w(
                "NotificationManagerHook: foreground relaunch failed. module=%s source=%s event=%s user=%s",
                modulePackage,
                sourcePackage,
                eventId,
                describeUserHandle(userHandle),
            )
        }
        return launched
    }

    private fun shouldAttemptForegroundRelaunchNow(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastForegroundRelaunchAttemptAt < FOREGROUND_RELAUNCH_COOLDOWN_MS) {
                return false
            }
            lastForegroundRelaunchAttemptAt = now
        }
        return true
    }

    private fun resolveUserId(userHandle: UserHandle): Int {
        return runCatching {
            val method = UserHandle::class.java.getMethod("getIdentifier")
            (method.invoke(userHandle) as? Int) ?: 0
        }.getOrDefault(0)
    }

    private fun isPackageStopped(systemContext: Context, packageName: String, userId: Int): Boolean {
        return runNonFatalCatching {
            val appInfo = getApplicationInfoForUser(systemContext, packageName, userId) ?: return false
            (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }.getOrElse { t ->
            XLog.w(
                "NotificationManagerHook: isPackageStopped failed. pkg=%s user=%d err=%s",
                packageName,
                userId,
                t.message ?: t.javaClass.simpleName,
            )
            false
        }
    }

    private fun getApplicationInfoForUser(systemContext: Context, packageName: String, userId: Int): ApplicationInfo? {
        val pm = systemContext.packageManager
        val asUser = runCatching {
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "getApplicationInfoAsUser" && it.parameterTypes.size == 3
            } ?: return@runCatching null
            method.invoke(pm, packageName, packageManagerMatchAllFlag(), userId) as? ApplicationInfo
        }.getOrNull()
        if (asUser != null) return asUser
        return getApplicationInfoCompat(pm, packageName)
    }

    private fun clearPackageStoppedState(systemContext: Context, packageName: String, userId: Int): Boolean {
        val pm = systemContext.packageManager
        val reflected = runCatching {
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "setPackageStoppedState" && it.parameterTypes.size == 3
            } ?: return@runCatching false
            method.invoke(pm, packageName, false, userId)
            true
        }.getOrDefault(false)
        if (reflected) return true

        val cmd = "cmd package set-stopped-state --user $userId $packageName false"
        return runShellCommand(cmd)
    }

    private fun runShellCommand(command: String): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                XLog.w("NotificationManagerHook: shell command failed(%d): %s", exitCode, command)
            }
            exitCode == 0
        }.getOrElse {
            XLog.w(
                "NotificationManagerHook: shell command exception: %s err=%s",
                command,
                it.message ?: it.javaClass.simpleName,
            )
            false
        }
    }

    private fun wakeRecoveryService(
        systemContext: Context,
        modulePackage: String,
        userHandle: UserHandle,
        reason: String,
        eventId: String,
    ): Boolean {
        val wakeIntent = Intent(NotificationHookConst.ACTION_FORCE_STOP_RECOVERY_WAKEUP).apply {
            setClassName(modulePackage, FORCE_STOP_RECOVERY_SERVICE_CLASS_NAME)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(NotificationHookConst.EXTRA_FORCE_STOP_REASON, reason)
            putExtra(NotificationHookConst.EXTRA_FORCE_STOP_EVENT_ID, eventId)
        }
        val startedAsUser = runCatching {
            HookHelpers.callMethod(systemContext, "startServiceAsUser", wakeIntent, userHandle)
            true
        }.getOrElse { false }
        if (startedAsUser) return true
        return runCatching { systemContext.startService(wakeIntent) != null }.getOrDefault(false)
    }

    private fun resolveTargetUsers(callerUid: Int): List<UserHandle> {
        val result = ArrayList<UserHandle>(2)
        runCatching {
            if (callerUid >= 0) {
                result.add(UserHandle.getUserHandleForUid(callerUid))
            }
        }.onFailure {
            XLog.w("NotificationManagerHook: resolve caller user failed. callerUid=%d", callerUid)
        }
        runCatching {
            val allUserHandle = UserHandle::class.java.getField("ALL").get(null) as? UserHandle
            if (allUserHandle != null && result.none { it == allUserHandle }) {
                result.add(allUserHandle)
            }
        }
        if (result.isEmpty()) {
            runCatching {
                val owner = UserHandle::class.java.getField("OWNER").get(null) as? UserHandle
                if (owner != null) result.add(owner)
            }
        }
        return result
    }

    private fun describeUserHandle(userHandle: UserHandle): String {
        return runCatching {
            val method = UserHandle::class.java.getMethod("getIdentifier")
            method.invoke(userHandle)?.toString() ?: userHandle.toString()
        }.getOrDefault(userHandle.toString())
    }

    private fun queryBroadcastReceiversCompat(
        packageManager: PackageManager,
        intent: Intent,
    ): List<ResolveInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(packageManagerMatchAllFlag().toLong()),
            )
        }
        val result = runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "queryBroadcastReceivers",
                Intent::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, intent, packageManagerMatchAllFlag())
        }
        return (result as? List<*>)?.filterIsInstance<ResolveInfo>().orEmpty()
    }

    private fun resolveContentProviderCompat(
        packageManager: PackageManager,
        authority: String,
    ): ProviderInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.resolveContentProvider(
                authority,
                PackageManager.ComponentInfoFlags.of(packageManagerMatchAllFlag().toLong()),
            )
        }
        return runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "resolveContentProvider",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, authority, packageManagerMatchAllFlag()) as? ProviderInfo
        }
    }

    private fun getPackageInfoCompat(
        packageManager: PackageManager,
        packageName: String,
    ): PackageInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(packageManagerMatchAllFlag().toLong()),
            )
        }
        return runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, packageName, packageManagerMatchAllFlag()) as? PackageInfo
        }
    }

    private fun getApplicationInfoCompat(
        packageManager: PackageManager,
        packageName: String,
    ): ApplicationInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(packageManagerMatchAllFlag().toLong()),
            )
        }
        return runNonFatalOrNull {
            val method = packageManager.javaClass.getMethod(
                "getApplicationInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(packageManager, packageName, packageManagerMatchAllFlag()) as? ApplicationInfo
        }
    }

    private fun contextIgnoreSecurityFlag(): Int {
        return resolveStaticIntFieldOrDefault(
            Context::class.java,
            "CONTEXT_IGNORE_SECURITY",
            CONTEXT_IGNORE_SECURITY_FALLBACK,
        )
    }

    private fun packageManagerMatchAllFlag(): Int {
        return resolveStaticIntFieldOrDefault(
            PackageManager::class.java,
            "MATCH_ALL",
            PACKAGE_MANAGER_MATCH_ALL_FALLBACK,
        )
    }

    private fun buildEventId(packageName: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((packageName + now).hashCode()).toString(36)
        return "nms_${now}_$suffix"
    }

    companion object {
        private const val MAINLAND_CHINA_COUNTRY_CODE = "86"
        private const val MAINLAND_CHINA_MOBILE_LENGTH = 11
        private const val FORWARD_RECEIVER_CLASS_NAME = "io.github.magisk317.relay.platform.ipc.ForwardReceiver"
        private const val FORCE_STOP_RECOVERY_SERVICE_CLASS_NAME =
            "io.github.magisk317.relay.service.ForceStopRecoveryService"
        private const val MAIN_ACTIVITY_CLASS_NAME = "io.github.magisk317.relay.ui.home.MainActivity"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val ENABLE_WECHAT_GROUP_SUMMARY_BYPASS = true
        private const val MSG_TYPE_SMS = "sms"
        private const val MSG_TYPE_APP_NOTIFY = "app_notify"
        private const val MSG_TYPE_CALL_NOTIFY = "call_notify"
        private const val CALL_TYPE_UNKNOWN = 0
        private const val CALL_TYPE_INCOMING = 1
        private const val CALL_TYPE_OUTGOING = 2
        private const val CALL_TYPE_MISSED = 3
        private const val CALL_TYPE_VOICEMAIL = 4
        private const val CALL_TYPE_REJECTED = 5
        private const val CALL_TYPE_BLOCKED = 6
        private const val CALL_TYPE_LABEL_DEFAULT = "通话通知"
        private const val CONTEXT_IGNORE_SECURITY_FALLBACK = 0x00000002
        private const val PACKAGE_MANAGER_MATCH_ALL_FALLBACK = 0x00020000
        private val CALL_NOTIFY_PACKAGE_ALLOWLIST = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.incallui",
        )
        private val TELEPHONY_SMS_PACKAGE_ALLOWLIST = setOf(
            "com.android.phone",
            "com.android.providers.telephony",
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )
        private val CJK_VERIFICATION_KEYWORDS = setOf(
            "验证码",
            "校验码",
            "检验码",
            "确认码",
            "激活码",
            "动态码",
            "安全码",
        )
        private val LATIN_VERIFICATION_KEYWORD_PATTERNS = listOf(
            Regex("(?<![a-z])verification(?![a-z])"),
            Regex("(?<![a-z])verify(?![a-z])"),
            Regex("(?<![a-z])otp(?![a-z])"),
            Regex("(?<![a-z])passcode(?![a-z])"),
            Regex("(?<![a-z])pin(?![a-z])"),
            Regex("(?<![a-z])code(?![a-z])"),
        )
        private val DATE_TIME_UNIT_TOKENS = setOf('年', '月', '日', '号', '时', '點', '点', '分', '秒')
        private val URL_CONTEXT_TOKENS = setOf('.', '/', ':', '?', '&', '=', '#', '%')
        private val VERIFICATION_CODE_PATTERN = Regex("\\b[A-Za-z0-9]{4,10}\\b")
        private val COMPANY_PATTERN = Regex("(?<=【)[^】]+(?=】)|(?<=\\[)[^]]+(?=])")
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
        private const val RECOVERY_COOLDOWN_MS = 3000L
        private const val FOREGROUND_RELAUNCH_COOLDOWN_MS = 60_000L
        private const val RECOVERY_PREF_CACHE_MS = 30_000L
    }
}
