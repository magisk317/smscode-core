package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object BrowserUtils {
    @JvmStatic
    fun openCustomTab(context: Context, url: String): Boolean = runCatching {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }.isSuccess

    /**
     * Open [url] in a Custom Tab. Returns null on success, otherwise the localized message for
     * [missingBrowserMessageResId] when no browser/Custom Tabs handler is available.
     */
    @JvmStatic
    fun openWebPage(context: Context, url: String, missingBrowserMessageResId: Int): String? {
        return if (openCustomTab(context, url)) {
            null
        } else {
            context.getString(missingBrowserMessageResId)
        }
    }
}
