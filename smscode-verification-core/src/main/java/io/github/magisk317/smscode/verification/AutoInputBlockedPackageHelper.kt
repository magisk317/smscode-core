package io.github.magisk317.smscode.verification

import android.content.Context
import android.net.Uri

object AutoInputBlockedPackageHelper {
    fun resolveBlockedState(
        packageName: String,
        primaryChecker: (String) -> Boolean?,
        fallbackChecker: (String) -> Boolean = { false },
        fallbackLogger: (String, Boolean) -> Unit = { _, _ -> },
    ): Boolean {
        primaryChecker(packageName)?.let { return it }
        val blocked = fallbackChecker(packageName)
        fallbackLogger(packageName, blocked)
        return blocked
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
        } catch (_: Exception) {
            null
        }
    }
}
