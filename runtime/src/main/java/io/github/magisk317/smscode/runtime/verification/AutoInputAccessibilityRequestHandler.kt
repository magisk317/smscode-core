package io.github.magisk317.smscode.runtime.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.xposed.logging.MagiskOtel

object AutoInputAccessibilityRequestHandler {
    fun handle(
        serviceContext: Context,
        receiver: BroadcastReceiver,
        intent: Intent,
        expectedAction: String,
        resultAction: String,
        packageName: String,
        performAutoInput: (AutoInputBroadcastContract.Request) -> AutoInputAccessibilityNodeHelper.Result,
        onResultBroadcastFailure: (Throwable) -> Unit = AutoInputAccessibilityResultPublisher::logTerminalResultFailure,
    ): Boolean {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null, strategy: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "source" to "accessibility",
            )
            if (reason != null) attrs["reason"] = reason
            if (strategy != null) attrs["strategy"] = strategy
            MagiskOtel.event(name = "auto.input", attributes = attrs, statusOk = statusOk)
        }

        val request = when (
            val receiverRequest = AutoInputBroadcastHelper.readReceiverRequest(
                intent = intent,
                expectedAction = expectedAction,
            )
        ) {
            AutoInputBroadcastContract.ReceiverRequest.Ignored -> return false
            is AutoInputBroadcastContract.ReceiverRequest.BlankCode -> {
                AutoInputBroadcastHelper.logReceiverRequest(
                    request = receiverRequest.request,
                    ordered = receiver.isOrderedBroadcast,
                )
                XLog.w("Accessibility auto input ignored: empty code")
                emit(result = "skip", reason = "empty_code")
                return true
            }
            is AutoInputBroadcastContract.ReceiverRequest.Accepted -> receiverRequest.request
        }
        AutoInputBroadcastHelper.logReceiverRequest(request, receiver.isOrderedBroadcast)

        val result = performAutoInput(request)
        XLog.w(
            "Accessibility auto input result: attemptId=%d success=%s strategy=%s reason=%s windowPkg=%s",
            request.attemptId ?: -1L,
            result.success,
            result.strategy,
            result.reason,
            result.windowPackage.ifBlank { "<none>" },
        )
        AutoInputAccessibilityResultPublisher.publishOrderedResult(
            receiver = receiver,
            result = result,
        )
        if (result.success) {
            AutoInputAccessibilityResultPublisher.publishTerminalResult(
                context = serviceContext,
                action = resultAction,
                packageName = packageName,
                attemptId = request.attemptId,
                result = result,
                token = intent.getStringExtra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN),
                onFailure = onResultBroadcastFailure,
            )
        }
        if (result.success && receiver.isOrderedBroadcast) {
            receiver.abortBroadcast()
        }
        emit(
            result = if (result.success) "ok" else "error",
            statusOk = result.success,
            reason = result.reason.ifBlank { null },
            strategy = result.strategy.ifBlank { null },
        )
        return true
    }
}
