package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.CursorIndexOutOfBoundsException
import android.database.SQLException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.os.RemoteException
import android.os.SystemClock
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import io.github.magisk317.xposed.logging.MagiskOtel

class OperateSmsActionHelper<M : SmsMessage>(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsMsg: M,
    private val deleteSmsEnabledReader: (Context) -> Boolean,
    private val markAsReadEnabledReader: (Context) -> Boolean,
) {
    enum class Operation {
        DELETE,
        MARK_AS_READ,
    }

    fun execute(forcedOperation: Operation? = null) {
        val startedAt = System.nanoTime()
        val operation = resolveOperation(
            forcedOperation,
            deleteSmsEnabledReader(pluginContext),
            markAsReadEnabledReader(pluginContext),
        )
        when (operation) {
            Operation.DELETE -> {
                val ok = deleteSms(smsMsg.sender, smsMsg.body)
                emitOperate(startedAt, result = if (ok) "ok" else "error", statusOk = ok, reason = "delete")
            }
            Operation.MARK_AS_READ -> {
                val ok = markSmsAsRead(smsMsg.sender, smsMsg.body)
                emitOperate(startedAt, result = if (ok) "ok" else "error", statusOk = ok, reason = "mark_as_read")
            }
            null -> emitOperate(startedAt, result = "skip", reason = "disabled")
        }
    }

    private fun emitOperate(
        startedAt: Long,
        result: String,
        statusOk: Boolean = true,
        reason: String? = null,
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "hook",
        )
        if (reason != null) attrs["reason"] = reason
        MagiskOtel.event(name = "sms.operate", attributes = attrs, statusOk = statusOk)
    }

    private fun markSmsAsRead(sender: String?, body: String?): Boolean {
        XLog.d("Marking SMS as read...")
        val report = operateSmsWithRetry(sender, body, Operation.MARK_AS_READ)
        if (report.success) {
            XLog.i("Mark SMS as read succeed")
        } else {
            XLog.i("Mark SMS as read failed")
            logSmsOpFailureDetails("mark_as_read", report)
        }
        return report.success
    }

    private fun deleteSms(sender: String?, body: String?): Boolean {
        XLog.d("Deleting SMS...")
        val report = operateSmsWhenInserted(sender, body, Operation.DELETE)
        if (report.success) {
            XLog.i("Delete SMS succeed")
        } else {
            XLog.i("Delete SMS failed")
            logSmsOpFailureDetails("delete", report)
        }
        return report.success
    }

    private fun operateSmsWithRetry(sender: String?, body: String?, smsOp: Operation): SmsOpRetryReport {
        var accumulatedScanned = 0
        var maxMatched = 0
        var lastReport = SmsOpAttemptReport()

        repeat(SMS_OP_RETRY_TIMES) { attempt ->
            val report = operateSms(sender, body, smsOp)
            lastReport = report
            accumulatedScanned += report.scannedRows
            if (report.matchedRows > maxMatched) maxMatched = report.matchedRows
            if (report.success) {
                return SmsOpRetryReport(
                    success = true,
                    attempts = attempt + 1,
                    scannedRows = accumulatedScanned,
                    maxMatchedRows = maxMatched,
                    lastAttempt = report,
                )
            }
            XLog.d(
                "SMS op retry pending: op=%s attempt=%d/%d scanned=%d matched=%d uri=%s targetId=%s rows=%d cursorNull=%s permissionDenied=%s err=%s",
                smsOpName(smsOp),
                attempt + 1,
                SMS_OP_RETRY_TIMES,
                report.scannedRows,
                report.matchedRows,
                report.uri,
                report.targetId ?: "-",
                report.rowsAffected,
                report.cursorNull,
                report.permissionDenied,
                report.error?.message ?: "-",
            )
            if (attempt < SMS_OP_RETRY_TIMES - 1) {
                SystemClock.sleep(SMS_OP_RETRY_DELAY_MS)
            }
        }
        return SmsOpRetryReport(
            success = false,
            attempts = SMS_OP_RETRY_TIMES,
            scannedRows = accumulatedScanned,
            maxMatchedRows = maxMatched,
            lastAttempt = lastReport,
        )
    }

    private fun operateSmsWhenInserted(sender: String?, body: String?, smsOp: Operation): SmsOpRetryReport {
        val initial = operateSms(sender, body, smsOp)
        if (initial.success) {
            return SmsOpRetryReport(
                success = true,
                attempts = 1,
                scannedRows = initial.scannedRows,
                maxMatchedRows = initial.matchedRows,
                lastAttempt = initial,
            )
        }
        if (smsOp != Operation.DELETE) {
            return SmsOpRetryReport(
                success = false,
                attempts = 1,
                scannedRows = initial.scannedRows,
                maxMatchedRows = initial.matchedRows,
                lastAttempt = initial,
            )
        }

        val latch = CountDownLatch(1)
        val changes = AtomicInteger(0)
        val scanned = AtomicInteger(initial.scannedRows)
        val maxMatched = AtomicInteger(initial.matchedRows)
        val lastAttempt = AtomicReference(initial)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changes.incrementAndGet()
                val report = operateSms(sender, body, smsOp)
                lastAttempt.set(report)
                scanned.addAndGet(report.scannedRows)
                updateMax(maxMatched, report.matchedRows)
                if (report.success) {
                    XLog.d(
                        "SMS op observer success: op=%s changeCount=%d uri=%s targetId=%s rows=%d",
                        smsOpName(smsOp),
                        changes.get(),
                        uri?.toString() ?: "-",
                        report.targetId ?: "-",
                        report.rowsAffected,
                    )
                    latch.countDown()
                } else {
                    XLog.d(
                        "SMS op observer pending: op=%s changeCount=%d scanned=%d matched=%d uri=%s targetId=%s rows=%d err=%s",
                        smsOpName(smsOp),
                        changes.get(),
                        report.scannedRows,
                        report.matchedRows,
                        report.uri,
                        report.targetId ?: "-",
                        report.rowsAffected,
                        report.error?.message ?: "-",
                    )
                }
            }
        }

        phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        return try {
            val completed = latch.await(SMS_DELETE_OBSERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                XLog.d(
                    "SMS op observer timeout: op=%s changes=%d scanned=%d matched=%d",
                    smsOpName(smsOp),
                    changes.get(),
                    scanned.get(),
                    maxMatched.get(),
                )
                val finalReport = operateSms(sender, body, smsOp)
                lastAttempt.set(finalReport)
                scanned.addAndGet(finalReport.scannedRows)
                updateMax(maxMatched, finalReport.matchedRows)
            }
            val final = lastAttempt.get()
            SmsOpRetryReport(
                success = final.success,
                attempts = 1 + changes.get() + if (completed) 0 else 1,
                scannedRows = scanned.get(),
                maxMatchedRows = maxMatched.get(),
                lastAttempt = final,
            )
        } finally {
            runCatching {
                phoneContext.contentResolver.unregisterContentObserver(observer)
            }.onFailure {
                XLog.w("Failed to unregister SMS observer", it)
            }
        }
    }

    private fun operateSms(sender: String?, body: String?, smsOp: Operation): SmsOpAttemptReport {
        var cursor: android.database.Cursor? = null
        var scannedRows = 0
        var matchedRows = 0
        var targetId: String? = null
        var rowsAffected = 0
        var lastError: Throwable? = null
        val mismatchSamples = mutableListOf<String>()
        val uriString = Telephony.Sms.CONTENT_URI.toString()
        fun recordFailure(error: Throwable) {
            lastError = error
            XLog.e("Operate SMS failed: ", error)
        }
        try {
            if (ContextCompat.checkSelfPermission(phoneContext, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                XLog.e("Don't have permission to read/write sms")
                return SmsOpAttemptReport(
                    success = false,
                    scannedRows = scannedRows,
                    matchedRows = matchedRows,
                    targetId = targetId,
                    rowsAffected = rowsAffected,
                    uri = uriString,
                    permissionDenied = true,
                )
            }
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.DATE,
            )
            val minDate = (smsMsg.date - SMS_MATCH_WINDOW_MS).coerceAtLeast(0L)
            val selection = "${Telephony.Sms.DATE} >= ?"
            val selectionArgs = arrayOf(minDate.toString())
            val sortOrder = Telephony.Sms.DATE + " desc limit " + SMS_QUERY_ROW_LIMIT
            val resolver = phoneContext.contentResolver
            val smsUri = Telephony.Sms.CONTENT_URI
            cursor = resolver.query(smsUri, projection, selection, selectionArgs, sortOrder)
            if (cursor == null) {
                XLog.d("Cursor is null")
                return SmsOpAttemptReport(
                    success = false,
                    scannedRows = scannedRows,
                    matchedRows = matchedRows,
                    targetId = targetId,
                    rowsAffected = rowsAffected,
                    uri = uriString,
                    cursorNull = true,
                )
            }
            while (cursor.moveToNext()) {
                scannedRows++
                val curAddress = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val curRead = cursor.getInt(cursor.getColumnIndexOrThrow("read"))
                val curBody = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                val curDate = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                val senderMatched = isAddressMatched(curAddress, sender)
                val bodyMatched = isBodyMatched(curBody, body, smsMsg.smsCode)
                if (!senderMatched || !bodyMatched) {
                    collectMismatchSample(
                        mismatchSamples = mismatchSamples,
                        curAddress = curAddress,
                        curBody = curBody,
                        curDate = curDate,
                        senderMatched = senderMatched,
                        bodyMatched = bodyMatched,
                    )
                    continue
                }
                matchedRows++
                if (smsOp == Operation.MARK_AS_READ && curRead != 0) {
                    return SmsOpAttemptReport(
                        success = true,
                        scannedRows = scannedRows,
                        matchedRows = matchedRows,
                        targetId = targetId,
                        rowsAffected = 0,
                        uri = uriString,
                    )
                }
                if (bodyMatched) {
                    val smsMessageId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                    targetId = smsMessageId
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"))
                    val where = Telephony.Sms._ID + " = ?"
                    val smsSelectionArgs = arrayOf(smsMessageId)
                    if (smsOp == Operation.DELETE) {
                        rowsAffected = resolver.delete(smsUri, where, smsSelectionArgs)
                        if (rowsAffected > 0) {
                            notifyExternalProviderChange()
                            return SmsOpAttemptReport(
                                success = true,
                                scannedRows = scannedRows,
                                matchedRows = matchedRows,
                                targetId = targetId,
                                rowsAffected = rowsAffected,
                                uri = uriString,
                            )
                        }
                    } else if (smsOp == Operation.MARK_AS_READ) {
                        val values = ContentValues()
                        values.put(Telephony.Sms.READ, true)
                        values.put(Telephony.Sms.SEEN, true)
                        rowsAffected = resolver.update(smsUri, values, where, smsSelectionArgs)
                        if (rowsAffected > 0) {
                            markRelatedSmsAsReadInThread(threadId, sender)
                            notifyExternalProviderChange()
                            return SmsOpAttemptReport(
                                success = true,
                                scannedRows = scannedRows,
                                matchedRows = matchedRows,
                                targetId = targetId,
                                rowsAffected = rowsAffected,
                                uri = uriString,
                            )
                        }
                    }
                }
            }
        } catch (error: SecurityException) {
            recordFailure(error)
        } catch (error: IllegalArgumentException) {
            recordFailure(error)
        } catch (error: IllegalStateException) {
            recordFailure(error)
        } catch (error: UnsupportedOperationException) {
            recordFailure(error)
        } catch (error: CursorIndexOutOfBoundsException) {
            recordFailure(error)
        } catch (error: SQLException) {
            recordFailure(error)
        } catch (error: OperationCanceledException) {
            recordFailure(error)
        } catch (error: RemoteException) {
            recordFailure(error)
        } finally {
            cursor?.close()
        }
        return SmsOpAttemptReport(
            success = false,
            scannedRows = scannedRows,
            matchedRows = matchedRows,
            targetId = targetId,
            rowsAffected = rowsAffected,
            uri = uriString,
            sample = mismatchSamples.joinToString(" | ").ifBlank { null },
            error = lastError,
        )
    }

    private fun markRelatedSmsAsReadInThread(threadId: Long, sender: String?) {
        if (threadId <= 0L || sender.isNullOrBlank()) return
        captureSmsProviderFailure(
            onFailure = { error ->
                XLog.w("Failed to mark related SMS as read in thread: $threadId", error)
            },
        ) {
            val where = (
                "${Telephony.Sms.THREAD_ID} = ? AND " +
                    "${Telephony.Sms.ADDRESS} = ? AND " +
                    "${Telephony.Sms.READ} = 0"
                )
            val selectionArgs = arrayOf(threadId.toString(), sender)
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, true)
                put(Telephony.Sms.SEEN, true)
            }
            val rows = pluginContext.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                where,
                selectionArgs,
            )
            if (rows > 0) {
                XLog.d("Marked $rows unread SMS as read in same thread: $threadId")
            }
        }
    }

    private fun collectMismatchSample(
        mismatchSamples: MutableList<String>,
        curAddress: String?,
        curBody: String?,
        curDate: Long,
        senderMatched: Boolean,
        bodyMatched: Boolean,
    ) {
        if (mismatchSamples.size >= SMS_MISMATCH_SAMPLE_LIMIT) return
        val closeByDate = abs(curDate - smsMsg.date) <= SMS_MATCH_WINDOW_MS
        if (!senderMatched && !bodyMatched && !closeByDate) return

        val bodyPreview = (curBody ?: "").replace("\n", "\\n").take(36)
        mismatchSamples += buildString {
            append("addr=")
            append(curAddress ?: "<null>")
            append(",dateDeltaMs=")
            append(abs(curDate - smsMsg.date))
            append(",senderMatched=")
            append(senderMatched)
            append(",bodyMatched=")
            append(bodyMatched)
            append(",body=")
            append(bodyPreview)
        }
    }

    private fun notifyExternalProviderChange() {
        captureSmsProviderFailure(
            onFailure = { error ->
                XLog.w("Failed to schedule external provider change broadcast", error)
            },
        ) {
            val packages = linkedSetOf<String>()
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(pluginContext)
            if (!defaultSmsPackage.isNullOrBlank()) {
                packages.add(defaultSmsPackage)
            }

            sendExternalProviderChange(packages)
            Handler(Looper.getMainLooper()).postDelayed(
                { sendExternalProviderChange(packages) },
                EXTERNAL_PROVIDER_CHANGE_DELAY_MS,
            )
        }
    }

    private fun sendExternalProviderChange(packages: Set<String>) {
        captureSmsProviderFailure(
            onFailure = { error ->
                XLog.w("Failed to send external provider change broadcast", error)
            },
        ) {
            packages.forEach { packageName ->
                val intent = Intent(Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE).apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    setPackage(packageName)
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                phoneContext.sendBroadcast(intent)
                XLog.d("Sent external provider change broadcast to: $packageName")
            }
        }
    }

    private inline fun captureSmsProviderFailure(
        onFailure: (Throwable) -> Unit,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (error: SecurityException) {
            onFailure(error)
        } catch (error: IllegalArgumentException) {
            onFailure(error)
        } catch (error: IllegalStateException) {
            onFailure(error)
        } catch (error: UnsupportedOperationException) {
            onFailure(error)
        } catch (error: CursorIndexOutOfBoundsException) {
            onFailure(error)
        } catch (error: SQLException) {
            onFailure(error)
        } catch (error: OperationCanceledException) {
            onFailure(error)
        } catch (error: RemoteException) {
            onFailure(error)
        }
    }

    private fun smsOpName(smsOp: Operation): String = if (smsOp == Operation.DELETE) "delete" else "mark_as_read"

    private fun updateMax(target: AtomicInteger, value: Int) {
        while (true) {
            val current = target.get()
            if (value <= current) return
            if (target.compareAndSet(current, value)) return
        }
    }

    private fun logSmsOpFailureDetails(opName: String, report: SmsOpRetryReport) {
        val last = report.lastAttempt
        XLog.w(
            "SMS op failed detail: op=%s attempts=%d scanned=%d maxMatched=%d uri=%s targetId=%s rows=%d cursorNull=%s permissionDenied=%s sample=%s err=%s",
            opName,
            report.attempts,
            report.scannedRows,
            report.maxMatchedRows,
            last.uri,
            last.targetId ?: "-",
            last.rowsAffected,
            last.cursorNull,
            last.permissionDenied,
            last.sample ?: "-",
            last.error?.message ?: "-",
        )
        last.error?.let {
            XLog.w("SMS op failed stacktrace", it)
        }
    }

    private data class SmsOpAttemptReport(
        val success: Boolean = false,
        val scannedRows: Int = 0,
        val matchedRows: Int = 0,
        val targetId: String? = null,
        val rowsAffected: Int = 0,
        val uri: String = Telephony.Sms.CONTENT_URI.toString(),
        val cursorNull: Boolean = false,
        val permissionDenied: Boolean = false,
        val sample: String? = null,
        val error: Throwable? = null,
    )

    private data class SmsOpRetryReport(
        val success: Boolean,
        val attempts: Int,
        val scannedRows: Int,
        val maxMatchedRows: Int,
        val lastAttempt: SmsOpAttemptReport,
    )

    companion object {
        fun resolveOperation(
            forcedOperation: Operation?,
            deleteSmsEnabled: Boolean,
            markAsReadEnabled: Boolean,
        ): Operation? {
            return forcedOperation ?: when {
                deleteSmsEnabled -> Operation.DELETE
                markAsReadEnabled -> Operation.MARK_AS_READ
                else -> null
            }
        }

        fun resolveForcedOperation(
            forcedOperation: Int?,
            deleteValue: Int,
            markAsReadValue: Int,
        ): Operation? {
            return when (forcedOperation) {
                deleteValue -> Operation.DELETE
                markAsReadValue -> Operation.MARK_AS_READ
                else -> null
            }
        }

        fun isAddressMatched(curAddress: String?, targetAddress: String?): Boolean {
            if (curAddress.isNullOrBlank() || targetAddress.isNullOrBlank()) return false
            if (curAddress == targetAddress) return true
            val normalizedCurrent = curAddress.filter { it.isDigit() }
            val normalizedTarget = targetAddress.filter { it.isDigit() }
            if (normalizedCurrent.isBlank() || normalizedTarget.isBlank()) {
                return false
            }
            return normalizedCurrent.endsWith(normalizedTarget) || normalizedTarget.endsWith(normalizedCurrent)
        }

        fun isBodyMatched(curBody: String?, targetBody: String?, targetCode: String?): Boolean {
            if (curBody.isNullOrEmpty() || targetBody.isNullOrEmpty()) return false
            if (curBody.startsWith(targetBody) || targetBody.startsWith(curBody)) return true

            val normalizedCur = curBody.filterNot { it.isWhitespace() }
            val normalizedTarget = targetBody.filterNot { it.isWhitespace() }
            if (normalizedCur.startsWith(normalizedTarget) || normalizedTarget.startsWith(normalizedCur)) {
                return true
            }

            val normalizedTargetCode = targetCode?.filter { it.isDigit() }
            if (!normalizedTargetCode.isNullOrBlank() && normalizedTargetCode.length >= 4) {
                val curDigits = curBody.filter { it.isDigit() }
                val targetDigits = targetBody.filter { it.isDigit() }
                if (curDigits.contains(normalizedTargetCode) && targetDigits.contains(normalizedTargetCode)) {
                    return true
                }
            }
            return false
        }

        private const val EXTERNAL_PROVIDER_CHANGE_DELAY_MS = 500L
        private const val SMS_DELETE_OBSERVER_TIMEOUT_MS = 45_000L
        private const val SMS_MATCH_WINDOW_MS = 5 * 60 * 1000L
        private const val SMS_QUERY_ROW_LIMIT = 200
        private const val SMS_MISMATCH_SAMPLE_LIMIT = 3
        private const val SMS_OP_RETRY_TIMES = 8
        private const val SMS_OP_RETRY_DELAY_MS = 1_200L
    }
}
