package io.github.magisk317.smscode.runtime.common.provider

import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult

/** Shared hook-side fingerprint query shape for the app-owned DBProvider. */
object HookFingerprintQuery {
    const val FINGERPRINT_SELECTION = "msg_type = ? AND sender = ? AND body = ? AND date = ?"

    fun query(
        context: Context,
        uri: Uri,
        sender: String?,
        body: String,
        date: Long,
        msgType: Int,
        onFailure: ((Throwable) -> Unit)? = null,
    ): SmsDuplicateCheckResult {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id"),
                FINGERPRINT_SELECTION,
                arrayOf(msgType.toString(), sender.orEmpty(), body, date.toString()),
                null,
            ) ?: return SmsDuplicateCheckResult.UNAVAILABLE
            cursor.use {
                if (it.moveToFirst()) {
                    SmsDuplicateCheckResult.DUPLICATE
                } else {
                    SmsDuplicateCheckResult.NOT_DUPLICATE
                }
            }
        } catch (error: Exception) {
            onFailure?.invoke(error)
            SmsDuplicateCheckResult.UNAVAILABLE
        }
    }
}
