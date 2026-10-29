package io.github.magisk317.forward.runtime.ingress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.forward.runtime.ingress.CustomMessageBroadcastContract
import io.github.magisk317.forward.runtime.ingress.CustomMessageBroadcastPayload
import io.github.magisk317.forward.runtime.ingress.IpcPayloadLimits
import io.github.magisk317.forward.runtime.ingress.CustomMessageReceiverPolicy
import io.github.magisk317.forward.runtime.ingress.ForwardReceiverPolicy

open class CustomMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        var traceId = "ipc_custom_${System.currentTimeMillis().toString(36)}"
        val startedAt = System.nanoTime()

        RECEIVER_SCOPE.launch {
            val env = ForwardIngressEnvironment.get()
            fun finish(code: Int, reason: String) {
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val ok = code == RESULT_OK
                MagiskOtel.event(
                    name = "sms.custom",
                    attributes = mapOf(
                        "result" to if (ok) "ok" else if (code == RESULT_DISPATCH_FAILED) "error" else "skip",
                        "duration_ms" to durationMs.toString(),
                        "process" to "main",
                        "reason" to reason,
                    ),
                    statusOk = ok || code != RESULT_DISPATCH_FAILED,
                )
                if (ordered) {
                    runCatching {
                        pendingResult.setResultCode(code)
                        pendingResult.setResultData(
                            "reason=$reason;${CustomMessageBroadcastContract.EXTRA_EVENT_ID}=${traceId.ifBlank { "<none>" }}",
                        )
                    }.onFailure { error ->
                        XLog.w("setting the ordered broadcast result failed: ${error.message}", error)
                    }
                }
                pendingResult.finish()
            }

            runCatching {
                if (intent.action != env.customMessageAction) {
                    finish(RESULT_REJECT_ACTION, "invalid_action")
                    return@runCatching
                }

                val expectedToken = env.expectedIpcToken()
                val receivedToken = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_IPC_TOKEN)
                if (!CustomMessageReceiverPolicy.isTokenAccepted(receivedToken, expectedToken)) {
                    finish(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@runCatching
                }

                val rawTargetSenderIds =
                    intent.getLongArrayExtra(CustomMessageBroadcastContract.EXTRA_TARGET_SENDER_IDS)
                if ((rawTargetSenderIds?.size ?: 0) > IpcPayloadLimits.MAX_TARGET_SENDER_IDS) {
                    finish(RESULT_REJECT_PAYLOAD, "target_sender_ids_too_large")
                    return@runCatching
                }
                val payload = CustomMessageBroadcastPayload.fromIntent(intent)
                val payloadRejection = IpcPayloadLimits.validateCustom(
                    payload = payload,
                    rawTargetSenderIdCount = rawTargetSenderIds?.size ?: 0,
                )
                if (payloadRejection != null) {
                    finish(RESULT_REJECT_PAYLOAD, payloadRejection)
                    return@runCatching
                }
                if (payload.message.isBlank()) {
                    finish(RESULT_REJECT_PAYLOAD, "message_missing")
                    return@runCatching
                }
                traceId = CustomMessageReceiverPolicy.resolveEventId(
                    eventId = payload.eventId,
                    message = payload.message,
                    title = payload.title,
                    packageName = payload.packageName,
                )
                val event = payload.toRelayEvent(sentFromPackage = resolveSentFromPackageCompat())
                val result = env.processEvent(event = event, traceId = traceId)
                if (result.dispatchError != null) {
                    finish(RESULT_DISPATCH_FAILED, "dispatch_error")
                    return@runCatching
                }
                finish(RESULT_OK, "accepted")
            }.onFailure { error ->
                XLog.e(message = "CustomMessageReceiver failed", error)
                finish(RESULT_DISPATCH_FAILED, "receiver_error")
            }
        }
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < ForwardReceiverPolicy.API_LEVEL_34) return null
        return runCatching { getSentFromPackage() }.getOrNull()
    }

    companion object {
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -201
        private const val RESULT_REJECT_TOKEN = -202
        private const val RESULT_REJECT_PAYLOAD = -203
        private const val RESULT_DISPATCH_FAILED = -204
        private val RECEIVER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
