package io.github.magisk317.smscode.runtime.common.autoinput

import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import io.github.magisk317.smscode.runtime.contract.ipc.IpcTokenMatcher
import io.github.magisk317.xposed.logging.MagiskOtel

object AutoInputResultProcessor {
    sealed interface ValidationResult {
        data object Ignored : ValidationResult

        data object MissingAttemptId : ValidationResult

        data class RejectedToken(
            val result: AutoInputResultBroadcastContract.Result,
            val expectedTokenEmpty: Boolean,
            val receivedTokenEmpty: Boolean,
        ) : ValidationResult

        data class Accepted(
            val result: AutoInputResultBroadcastContract.Result,
        ) : ValidationResult
    }

    enum class PersistenceOutcome {
        UPDATED,
        UPSERTED,
        STALE,
    }

    suspend fun validate(
        intent: Intent,
        expectedAction: String,
        expectedTokenProvider: suspend () -> String?,
    ): ValidationResult {
        val result = when (
            val receiverResult = AutoInputResultBroadcastContract.readResult(intent, expectedAction)
        ) {
            AutoInputResultBroadcastContract.ReceiverResult.Ignored -> return ValidationResult.Ignored
            AutoInputResultBroadcastContract.ReceiverResult.MissingAttemptId -> {
                emitResult(result = "error", statusOk = false, reason = "missing_attempt_id")
                return ValidationResult.MissingAttemptId
            }
            is AutoInputResultBroadcastContract.ReceiverResult.Accepted -> receiverResult.result
        }

        val expectedToken = expectedTokenProvider()
        val receivedToken = intent.getStringExtra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN)
        if (!IpcTokenMatcher.matches(expectedToken, receivedToken)) {
            emitResult(result = "error", statusOk = false, reason = "token_rejected")
            return ValidationResult.RejectedToken(
                result = result,
                expectedTokenEmpty = expectedToken.isNullOrBlank(),
                receivedTokenEmpty = receivedToken.isNullOrBlank(),
            )
        }
        emitResult(
            result = if (result.success) "ok" else "error",
            statusOk = result.success,
            reason = result.reason ?: if (result.success) "accepted" else "failed",
        )
        return ValidationResult.Accepted(result)
    }

    private fun emitResult(
        result: String,
        statusOk: Boolean = true,
        reason: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "result",
            "source" to "auto_input_result",
        )
        if (reason != null) attrs["reason"] = reason
        MagiskOtel.event(name = "auto.input", attributes = attrs, statusOk = statusOk)
    }

    suspend fun persist(
        result: AutoInputResultBroadcastContract.Result,
        update: suspend (AutoInputResultBroadcastContract.Result) -> Long,
        upsert: suspend (AutoInputResultBroadcastContract.Result) -> Long,
    ): PersistenceOutcome {
        if (update(result) > 0L) return PersistenceOutcome.UPDATED
        return if (upsert(result) > 0L) {
            PersistenceOutcome.UPSERTED
        } else {
            PersistenceOutcome.STALE
        }
    }
}
