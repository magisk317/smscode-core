package io.github.magisk317.smscode.verification

object SmsCodePlanFactory {
    fun loadSettings(prefs: VerificationPrefs): SmsCodePostParseCoordinator.Settings {
        return SmsCodePostParseCoordinator.Settings(
            showNotification = prefs.showNotification(),
            autoCancelNotification = prefs.autoCancelNotification(),
            notificationRetentionMs = prefs.notificationRetentionMs(),
            autoInputEnabled = prefs.autoInputEnabled(),
            autoInputDelayMs = prefs.autoInputDelayMs(),
            copyToClipboardEnabled = prefs.copyToClipboardEnabled(),
            showToast = prefs.showToast(),
            recordSmsEnabled = prefs.recordSmsEnabled(),
            blockSmsEnabled = prefs.blockSmsEnabled(),
            markAsReadEnabled = prefs.markAsReadEnabled(),
            deleteSmsEnabled = prefs.deleteSmsEnabled(),
            deduplicateSmsEnabled = prefs.deduplicateSmsEnabled(),
        )
    }

    fun resolveOperateSmsDelays(
        settings: SmsCodePostParseCoordinator.Settings,
    ): List<Long> {
        return when {
            settings.deleteSmsEnabled -> DELETE_SMS_DELAYS_MS
            settings.markAsReadEnabled -> MARK_AS_READ_RETRY_DELAYS_MS
            else -> emptyList()
        }
    }

    fun createParsedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ParsedSmsPlan {
        return SmsCodePostParseCoordinator.ParsedSmsPlan(
            blockSms = settings.blockSmsEnabled,
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            uiPlan = SmsCodePostParseCoordinator.UiPlan(
                copyToClipboardEnabled = settings.copyToClipboardEnabled,
                showToast = settings.showToast,
            ),
            autoInputDelayMs = if (settings.autoInputEnabled) settings.autoInputDelayMs else null,
            notificationPlan = if (settings.showNotification) {
                SmsCodePostParseCoordinator.NotificationPlan(
                    autoCancelDelayMs = if (settings.autoCancelNotification) settings.notificationRetentionMs else null,
                )
            } else {
                null
            },
            shouldRecord = settings.recordSmsEnabled,
            operateSmsDelays = resolveOperateSmsDelays(settings),
        )
    }

    fun createObservedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            autoInputEnabled = settings.autoInputEnabled,
            autoInputDelayMs = if (settings.autoInputEnabled) settings.autoInputDelayMs else null,
            shouldRecord = settings.recordSmsEnabled && !settings.deduplicateSmsEnabled,
        )
    }

    private val MARK_AS_READ_RETRY_DELAYS_MS = listOf(300L, 1000L, 2000L)
    private val DELETE_SMS_DELAYS_MS = listOf(300L)
}
