package io.github.magisk317.smscode.xposed.hook.telephony

import android.os.Binder
import io.github.magisk317.smscode.xposed.utils.XLog

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
        XLog.w("Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = clearCallingIdentity()
        var deleteSucceeded = false
        try {
            methodInvoker.deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
            deleteSucceeded = true
        } catch (error: ReflectiveOperationException) {
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: SecurityException) {
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: IllegalArgumentException) {
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.e("Error occurs when delete SMS data from raw table", error)
        } finally {
            restoreCallingIdentity(token)
        }

        var sendSucceeded = false
        try {
            methodInvoker.sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
            sendSucceeded = true
        } catch (error: ReflectiveOperationException) {
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: SecurityException) {
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: IllegalArgumentException) {
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: IllegalStateException) {
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: UnsupportedOperationException) {
            XLog.e("Error occurs when sending broadcast complete", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.e("Error occurs when sending broadcast complete", error)
        }
        return deleteSucceeded && sendSucceeded
    }
}
