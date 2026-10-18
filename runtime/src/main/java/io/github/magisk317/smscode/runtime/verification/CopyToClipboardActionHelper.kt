package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

object CopyToClipboardActionHelper {
    fun copyCode(
        phoneContext: Context,
        smsCode: String?,
        copyAction: (Context, String?) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "code_length" to (smsCode?.length ?: 0).toString(),
            )
            if (reason != null) {
                attrs["reason"] = reason
            }
            MagiskOtel.event(
                name = "sms.copy",
                attributes = attrs,
                statusOk = statusOk,
            )
        }

        if (smsCode.isNullOrBlank()) {
            emit(result = "skip", reason = "empty_code")
            return
        }
        try {
            XLog.d("Attempting to copy code to clipboard with context: %s", phoneContext)
            copyAction(phoneContext, smsCode)
            emit(result = "ok")
        } catch (error: SecurityException) {
            XLog.e("Failed to copy to clipboard", error)
            emit(result = "error", statusOk = false, reason = "SecurityException")
        } catch (error: IllegalArgumentException) {
            XLog.e("Failed to copy to clipboard", error)
            emit(result = "error", statusOk = false, reason = "IllegalArgumentException")
        } catch (error: IllegalStateException) {
            XLog.e("Failed to copy to clipboard", error)
            emit(result = "error", statusOk = false, reason = "IllegalStateException")
        } catch (error: UnsupportedOperationException) {
            XLog.e("Failed to copy to clipboard", error)
            emit(result = "error", statusOk = false, reason = "UnsupportedOperationException")
        }
    }
}
