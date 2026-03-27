package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class CodeWorker<M : SmsMessage, R : SmsParseResult>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIntent: Intent,
    private val eventId: String = "",
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings,
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ParsedSmsPlan =
        SmsCodePostParseCoordinator::createParsedSmsPlan,
    private val moduleEnabledReader: (Context) -> Boolean,
    private val verboseLogReader: (Context) -> Boolean,
    private val logLevelSetter: (Int) -> Unit,
    private val currentLogLevelReader: () -> Int,
    private val defaultLogLevel: Int,
    private val parseRunner: (
        ScheduledExecutorService,
        Context,
        Context,
        Intent,
        Boolean,
    ) -> ParseOutcome<M>?,
    private val parsedSmsDispatcher: (
        Handler,
        ScheduledExecutorService,
        Context,
        Context,
        M,
        String,
        SmsCodePostParseCoordinator.ParsedSmsPlan,
    ) -> Unit,
    private val afterDispatch: (
        ScheduledExecutorService,
        Context,
        Context,
        M,
        SmsCodePostParseCoordinator.ParsedSmsPlan,
    ) -> Unit = { _, _, _, _, _ -> },
    private val parseResultFactory: (Boolean) -> R,
    private val uiHandler: Handler = Handler(Looper.getMainLooper()),
    private val executorFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
) {
    data class ParseOutcome<M : SmsMessage>(
        val smsMsg: M? = null,
        val duplicated: Boolean = false,
    )

    fun parse(): R? {
        val executor = executorFactory()
        val settings = settingsLoader(pluginContext)
        val plan = planFactory(settings)
        val moduleEnabled = moduleEnabledReader(pluginContext)
        val verboseLog = verboseLogReader(pluginContext)
        XLog.w(
            "Diag settings: event_id=%s enabled=%s, verbose=%s, showNotif=%s, autoCancel=%s, " +
                "retentionSec=%d, autoInput=%s, copy=%s, toast=%s, record=%s, " +
                "block=%s, markRead=%s, delete=%s, dedup=%s",
            eventId.ifBlank { "<none>" },
            moduleEnabled,
            verboseLog,
            settings.showNotification,
            settings.autoCancelNotification,
            settings.notificationRetentionMs / 1000L,
            settings.autoInputEnabled,
            settings.copyToClipboardEnabled,
            settings.showToast,
            settings.recordSmsEnabled,
            settings.blockSmsEnabled,
            settings.markAsReadEnabled,
            settings.deleteSmsEnabled,
            settings.deduplicateSmsEnabled,
        )

        if (!moduleEnabled) {
            XLog.w("Diag: module disabled in settings")
            XLog.i("XposedSmsCode disabled, exiting")
            executor.shutdown()
            return null
        }
        if (verboseLog) {
            logLevelSetter(Log.VERBOSE)
        } else {
            logLevelSetter(defaultLogLevel)
        }
        XLog.w(
            "Diag log mode: verboseSetting=%s, activeLevel=%d, defaultLevel=%d",
            verboseLog,
            currentLogLevelReader(),
            defaultLogLevel,
        )

        return try {
            val parseOutcome = parseRunner(
                executor,
                pluginContext,
                phoneContext,
                smsIntent,
                settings.deduplicateSmsEnabled,
            ) ?: run {
                executor.shutdown()
                return null
            }
            if (parseOutcome.duplicated) {
                executor.shutdown()
                return parseResultFactory(plan.blockSms)
            }

            val smsMsg = parseOutcome.smsMsg ?: run {
                executor.shutdown()
                return null
            }

            parsedSmsDispatcher(
                uiHandler,
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan,
            )
            afterDispatch(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                plan,
            )

            executor.shutdown()
            parseResultFactory(plan.blockSms)
        } catch (error: Exception) {
            executor.shutdown()
            XLog.e("Error occurs when get SmsParseAction call value", error)
            null
        }
    }
}
