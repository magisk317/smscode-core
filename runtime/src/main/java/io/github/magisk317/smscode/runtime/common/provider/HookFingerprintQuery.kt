package io.github.magisk317.smscode.runtime.common.provider

import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult

/** Shared hook-side fingerprint query shape for the app-owned DBProvider. */
object HookFingerprintQuery {
    const val FINGERPRINT_SELECTION = "msg_type = ? AND sender = ? AND body = ? AND date = ?"
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val MAX_AUTHORITY_LENGTH = 64
    private const val MAX_EXCEPTION_NAME_LENGTH = 40

    enum class UnavailableReason(val value: String) {
        NULL_CURSOR("null_cursor"),
        EXCEPTION("exception"),
    }

    /** Never carries a query URI, exception message, selection arguments, or SMS content. */
    data class Unavailable(
        val reason: UnavailableReason,
        val authority: String?,
        val elapsedMs: Long,
        val exceptionClass: String? = null,
    ) {
        /** Bound each field so the error type survives the hook logger's line limit. */
        fun toLogMessage(): String =
            "SMS dedup unavailable reason=${reason.value} ms=$elapsedMs " +
                "authority=${authority.orEmpty().take(MAX_AUTHORITY_LENGTH)} " +
                "error=${exceptionClass?.substringAfterLast('.')?.take(MAX_EXCEPTION_NAME_LENGTH) ?: "none"}"
    }

    /**
     * [onUnavailable] reports both null cursors and exceptions, and supersedes legacy [onFailure]
     * when supplied. Diagnostic callbacks cannot change the result or escape into telephony.
     */
    @Suppress("TooGenericExceptionCaught")
    fun query(
        context: Context,
        uri: Uri,
        sender: String?,
        body: String,
        date: Long,
        msgType: Int,
        onFailure: ((Throwable) -> Unit)? = null,
        onUnavailable: ((Unavailable) -> Unit)? = null,
    ): SmsDuplicateCheckResult {
        val startedAt = System.nanoTime()
        var failure: Exception? = null
        val result = try {
            context.contentResolver.query(
                uri,
                arrayOf("_id"),
                FINGERPRINT_SELECTION,
                arrayOf(msgType.toString(), sender.orEmpty(), body, date.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    SmsDuplicateCheckResult.DUPLICATE
                } else {
                    SmsDuplicateCheckResult.NOT_DUPLICATE
                }
            } ?: SmsDuplicateCheckResult.UNAVAILABLE
        } catch (error: Exception) {
            failure = error
            SmsDuplicateCheckResult.UNAVAILABLE
        }
        if (result == SmsDuplicateCheckResult.UNAVAILABLE) {
            val elapsedMs = ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)
            // Best-effort diagnostics: a throwing callback must not escape into telephony.
            runCatching {
                if (onUnavailable != null) {
                    onUnavailable(
                        Unavailable(
                            reason = if (failure == null) UnavailableReason.NULL_CURSOR else UnavailableReason.EXCEPTION,
                            authority = uri.authority,
                            elapsedMs = elapsedMs,
                            exceptionClass = failure?.javaClass?.name,
                        ),
                    )
                } else {
                    failure?.let { onFailure?.invoke(it) }
                }
            }.onFailure { }
        }
        return result
    }
}
