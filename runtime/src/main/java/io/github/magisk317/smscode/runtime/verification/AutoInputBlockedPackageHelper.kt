package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.SQLException
import android.net.Uri
import io.github.magisk317.xposed.logging.MagiskOtel
import android.os.OperationCanceledException

object AutoInputBlockedPackageHelper {
    fun resolveBlockedState(
        packageName: String,
        primaryChecker: (String) -> Boolean?,
        fallbackChecker: (String) -> Boolean = { false },
        fallbackLogger: (String, Boolean) -> Unit = { _, _ -> },
    ): Boolean {
        primaryChecker(packageName)?.let { blocked ->
            emitBlockedState(
                packageName = packageName,
                blocked = blocked,
                reason = if (blocked) "primary_blocked" else "primary_allowed",
            )
            return blocked
        }
        val blocked = fallbackChecker(packageName)
        fallbackLogger(packageName, blocked)
        emitBlockedState(
            packageName = packageName,
            blocked = blocked,
            reason = if (blocked) "fallback_blocked" else "fallback_allowed",
        )
        return blocked
    }

    private fun emitBlockedState(
        packageName: String,
        blocked: Boolean,
        reason: String,
    ) {
        MagiskOtel.event(
            name = "auto.input",
            attributes = mapOf(
                "result" to if (blocked) "skip" else "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "blocked_package",
                "reason" to reason,
                "target_package" to packageName,
            ),
            statusOk = true,
        )
    }

    fun queryBlockedStateByProvider(
        context: Context,
        packageName: String,
        uriResolver: (String) -> Uri,
        columnName: String = "blocked",
    ): Boolean? {
        return try {
            val uri = uriResolver(packageName)
            context.contentResolver.query(uri, arrayOf(columnName), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return false
                }
                val index = cursor.getColumnIndex(columnName)
                if (index < 0) return false
                when (cursor.getType(index)) {
                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(index) != 0
                    android.database.Cursor.FIELD_TYPE_STRING -> {
                        val raw = cursor.getString(index).orEmpty()
                        raw == "1" || raw.equals("true", ignoreCase = true)
                    }
                    else -> false
                }
            } ?: false
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: SQLException) {
            null
        } catch (_: CursorIndexOutOfBoundsException) {
            null
        } catch (_: OperationCanceledException) {
            null
        }
    }
}
