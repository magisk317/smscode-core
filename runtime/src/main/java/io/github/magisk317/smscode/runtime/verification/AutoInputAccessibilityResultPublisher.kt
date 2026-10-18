package io.github.magisk317.smscode.runtime.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputAccessibilityResultContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import io.github.magisk317.xposed.logging.XLog

object AutoInputAccessibilityResultPublisher {
    fun publishOrderedResult(
        receiver: BroadcastReceiver,
        result: AutoInputAccessibilityNodeHelper.Result,
    ) {
        if (!receiver.isOrderedBroadcast) return
        val extras = runCatching { receiver.getResultExtras(true) }.getOrElse { Bundle() }
        AutoInputAccessibilityResultContract.putOrderedResult(
            extras = extras,
            result = AutoInputAccessibilityResultContract.OrderedResult(
                success = result.success,
                strategy = result.strategy,
                reason = result.reason,
                windowPackage = result.windowPackage,
            ),
        )
        receiver.setResultExtras(extras)
    }

    fun buildTerminalResultIntent(
        action: String,
        packageName: String,
        attemptId: Long?,
        result: AutoInputAccessibilityNodeHelper.Result,
        token: String? = null,
        intentFactory: (String) -> Intent = ::Intent,
    ): Intent? {
        val resolvedAttemptId = attemptId ?: return null
        return AutoInputResultBroadcastContract.buildResultIntent(
            action = action,
            packageName = packageName,
            result = AutoInputResultBroadcastContract.Result(
                attemptId = resolvedAttemptId,
                success = result.success,
                reason = result.reason,
            ),
            token = AutoInputBroadcastContract.normalizeIpcToken(token),
            intentFactory = intentFactory,
        )
    }

    fun publishTerminalResult(
        context: Context,
        action: String,
        packageName: String,
        attemptId: Long?,
        result: AutoInputAccessibilityNodeHelper.Result,
        token: String?,
        onFailure: (Throwable) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        fun emit(resultToken: String, statusOk: Boolean, reason: String?) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to resultToken,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "a11y_publish",
                "source" to "accessibility",
            )
            if (reason != null) attrs["reason"] = reason
            if (result.strategy.isNotBlank()) attrs["strategy"] = result.strategy
            MagiskOtel.event(name = "auto.input", attributes = attrs, statusOk = statusOk)
        }

        val intent = buildTerminalResultIntent(
            action = action,
            packageName = packageName,
            attemptId = attemptId,
            result = result,
            token = token,
        )
        if (intent == null) {
            emit(resultToken = "skip", statusOk = true, reason = "missing_attempt_id")
            return
        }
        runCatching { context.sendBroadcast(intent) }
            .onSuccess {
                emit(
                    resultToken = if (result.success) "ok" else "error",
                    statusOk = result.success,
                    reason = result.reason.ifBlank { "published" },
                )
            }
            .onFailure { error ->
                emit(
                    resultToken = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName.ifBlank { "publish_failed" },
                )
                onFailure(error)
            }
    }

    fun logTerminalResultFailure(error: Throwable) {
        XLog.w(
            message = "Accessibility auto input result broadcast failed: %s",
            error.message ?: error.javaClass.simpleName,
        )
    }

    val EXTRA_ATTEMPT_ID: String
        get() = AutoInputResultBroadcastContract.EXTRA_ATTEMPT_ID
    val EXTRA_SUCCESS: String
        get() = AutoInputResultBroadcastContract.EXTRA_SUCCESS
    val EXTRA_REASON: String
        get() = AutoInputResultBroadcastContract.EXTRA_REASON
}
