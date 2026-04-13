package io.github.magisk317.smscode.runtime.common.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

object ClipboardUtils {
    private const val LOG_TAG = "runtime-common"

    @JvmStatic
    fun copyToClipboard(context: Context, text: String?) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (cm == null) {
            Log.e(LOG_TAG, "Copy failed, clipboard manager is null")
            return
        }
        val clipData = ClipData.newPlainText("Copy text", text)
        cm.setPrimaryClip(clipData)
        Log.i(LOG_TAG, "Copy to clipboard succeed")
    }

    @JvmStatic
    fun clearClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (cm == null) {
            Log.e(LOG_TAG, "Clear failed, clipboard manager is null")
            return
        }
        if (cm.hasPrimaryClip()) {
            val cd = cm.primaryClipDescription
            if (cd != null) {
                if (cd.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    cm.setPrimaryClip(ClipData.newPlainText("Copy text", ""))
                    Log.i(LOG_TAG, "Clear clipboard succeed")
                }
            }
        }
    }
}
