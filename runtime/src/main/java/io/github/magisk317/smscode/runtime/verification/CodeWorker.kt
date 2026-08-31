package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.smscode.verification.SmsParseResult

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
            )
            if (reason != null) {
                attrs["reason"] = reason
            }
            if (eventId.isNotBlank()) {
                attrs["event_id_present"] = "true"
            }
            MagiskOtel.event(
                name = "sms.pipeline",
                attributes = attrs,
                statusOk = statusOk,
            )
        }

        val executor = executorFactory()
        return try {
            val settings = settingsLoader(pluginContext)
            val plan = planFactory(settings)
            val moduleEnabled = moduleEnabledReader(pluginContext)
            val verboseLog = verboseLogReader(pluginContext)
            XLog.i(
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
                emit(result = "skip", reason = "module_disabled")
                return null
            }
            if (verboseLog) {
                logLevelSetter(Log.VERBOSE)
            } else {
                logLevelSetter(defaultLogLevel)
            }
            XLog.i(
                "Diag log mode: verboseSetting=%s, activeLevel=%d, defaultLevel=%d",
                verboseLog,
                currentLogLevelReader(),
                defaultLogLevel,
            )

            val parseOutcome = parseRunner(
                executor,
                pluginContext,
                phoneContext,
                smsIntent,
                settings.deduplicateSmsEnabled,
            ) ?: run {
                XLog.w("Diag parseRunner returned null: event_id=%s", eventId)
                emit(result = "skip", reason = "parse_null")
                return null
            }
            if (parseOutcome.duplicated) {
                XLog.w("Diag parseOutcome duplicated: event_id=%s", eventId)
                emit(result = "skip", reason = "duplicate")
                return parseResultFactory(plan.blockSms)
            }

            val smsMsg = parseOutcome.smsMsg ?: run {
                XLog.w("Diag parseOutcome.smsMsg is null: event_id=%s", eventId)
                emit(result = "skip", reason = "sms_null")
                return null
            }

            XLog.i("Diag dispatching actions: event_id=%s smsCode=%s notifPlan=%s autoInput=%s",
                eventId, smsMsg.smsCode, plan.notificationPlan != null, plan.autoInputDelayMs)

            try {
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
            } catch (error: RejectedExecutionException) {
                XLog.e("Error occurs when scheduling parsed SMS actions", error)
                emit(result = "error", statusOk = false, reason = "RejectedExecutionException")
                return null
            } catch (error: IllegalArgumentException) {
                XLog.e("Error occurs when scheduling parsed SMS actions", error)
                emit(result = "error", statusOk = false, reason = "IllegalArgumentException")
                return null
            } catch (error: IllegalStateException) {
                XLog.e("Error occurs when scheduling parsed SMS actions", error)
                emit(result = "error", statusOk = false, reason = "IllegalStateException")
                return null
            } catch (error: SecurityException) {
                XLog.e("Error occurs when scheduling parsed SMS actions", error)
                emit(result = "error", statusOk = false, reason = "SecurityException")
                return null
            } catch (error: UnsupportedOperationException) {
                XLog.e("Error occurs when scheduling parsed SMS actions", error)
                emit(result = "error", statusOk = false, reason = "UnsupportedOperationException")
                return null
            }

            emit(result = "ok")
            parseResultFactory(plan.blockSms)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "InterruptedException")
            null
        } catch (error: ExecutionException) {
            XLog.e("Error occurs when running SmsParseAction", error.cause ?: error)
            emit(result = "error", statusOk = false, reason = "ExecutionException")
            null
        } catch (error: CancellationException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "CancellationException")
            null
        } catch (error: RejectedExecutionException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "RejectedExecutionException")
            null
        } catch (error: IllegalArgumentException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "IllegalArgumentException")
            null
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "IllegalStateException")
            null
        } catch (error: SecurityException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "SecurityException")
            null
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when running SmsParseAction", error)
            emit(result = "error", statusOk = false, reason = "UnsupportedOperationException")
            null
        } finally {
            executor.shutdown()
        }
    }
}
