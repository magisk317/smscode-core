package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog

object CopyToClipboardActionHelper {
    fun copyCode(
        phoneContext: Context,
        smsCode: String?,
        copyAction: (Context, String?) -> Unit,
    ) {
        try {
            XLog.d("Attempting to copy code to clipboard with context: %s", phoneContext)
            copyAction(phoneContext, smsCode)
        } catch (error: SecurityException) {
            XLog.e("Failed to copy to clipboard", error)
        } catch (error: IllegalArgumentException) {
            XLog.e("Failed to copy to clipboard", error)
        } catch (error: IllegalStateException) {
            XLog.e("Failed to copy to clipboard", error)
        } catch (error: UnsupportedOperationException) {
            XLog.e("Failed to copy to clipboard", error)
        }
    }
}
