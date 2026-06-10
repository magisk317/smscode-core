package io.github.magisk317.smscode.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.xposed.utils.XLog

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
                onFailure = onResultBroadcastFailure,
            )
        }
        if (result.success && receiver.isOrderedBroadcast) {
            receiver.abortBroadcast()
        }
        return true
    }
}
