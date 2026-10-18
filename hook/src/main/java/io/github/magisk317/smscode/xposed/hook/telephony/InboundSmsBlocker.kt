package io.github.magisk317.smscode.xposed.hook.telephony

import android.os.Binder
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.MagiskOtel

class InboundSmsBlocker(
    private val smsHandlerClassName: String,
    private val methodInvoker: InboundSmsMethodInvoker = InboundSmsMethodInvoker(smsHandlerClassName),
    private val clearCallingIdentity: () -> Long = Binder::clearCallingIdentity,
    private val restoreCallingIdentity: (Long) -> Unit = Binder::restoreCallingIdentity,
) {
    fun blockInboundSms(
        inboundSmsHandler: Any,
        smsReceiver: Any,
        reason: String,
        eventId: String,
    ): Boolean {
        val startedAt = System.nanoTime()
        XLog.w(message = "Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = clearCallingIdentity()
        var deleteSucceeded = false
        var deleteError: String? = null
        try {
            methodInvoker.deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
            deleteSucceeded = true
        } catch (error: ReflectiveOperationException) {
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: SecurityException) {
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: IllegalArgumentException) {
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: IllegalStateException) {
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: UnsupportedOperationException) {
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            deleteError = error.javaClass.simpleName
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } finally {
            restoreCallingIdentity(token)
        }

        var sendSucceeded = false
        var sendError: String? = null
        try {
            methodInvoker.sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
            sendSucceeded = true
        } catch (error: ReflectiveOperationException) {
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: SecurityException) {
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: IllegalArgumentException) {
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: IllegalStateException) {
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: UnsupportedOperationException) {
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            sendError = error.javaClass.simpleName
            XLog.e("Error occurs when sending broadcast complete", error)
        }

        val ok = deleteSucceeded && sendSucceeded
        val attrs = mutableMapOf(
            "result" to if (ok) "ok" else "error",
            "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
            "process" to "hook",
            "stage" to "inbound_raw",
            "reason" to reason.ifBlank { "unknown" },
            "event_id_present" to eventId.isNotBlank().toString(),
            "delete_ok" to deleteSucceeded.toString(),
            "complete_ok" to sendSucceeded.toString(),
        )
        val errorClass = deleteError ?: sendError
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "sms.block", attributes = attrs, statusOk = ok)
        return ok
    }
}
