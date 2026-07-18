package io.github.magisk317.smscode.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputAccessibilityResultContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract

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
        val intent = buildTerminalResultIntent(
            action = action,
            packageName = packageName,
            attemptId = attemptId,
            result = result,
            token = token,
        ) ?: return
        runCatching { context.sendBroadcast(intent) }
            .onFailure(onFailure)
    }

    fun logTerminalResultFailure(error: Throwable) {
        XLog.w(
            "Accessibility auto input result broadcast failed: %s",
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
