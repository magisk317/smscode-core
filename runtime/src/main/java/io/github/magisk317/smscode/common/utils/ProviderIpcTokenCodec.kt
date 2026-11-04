package io.github.magisk317.smscode.common.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.xposed.runtime.RuntimeStateProviderClient
import io.github.magisk317.xposed.runtime.ipc.IpcTokenMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Pure IPC-token transport and matching helpers for exported module providers. */
object ProviderIpcTokenCodec {
    const val PARAM_IPC_TOKEN = "ipc_token"
    const val EXTRA_IPC_TOKEN = RuntimeStateProviderClient.EXTRA_IPC_TOKEN

    fun attachToken(uri: Uri, token: String): Uri =
        uri.buildUpon().appendQueryParameter(PARAM_IPC_TOKEN, token).build()

    fun presentedToken(uri: Uri?): String? =
        uri?.getQueryParameter(PARAM_IPC_TOKEN)?.takeIf { it.isNotBlank() }

    fun presentedToken(extras: Bundle?): String? =
        extras?.getString(EXTRA_IPC_TOKEN)?.takeIf { it.isNotBlank() }

    fun evaluate(callerAllowed: Boolean, expectedToken: String?, presentedToken: String?): Boolean =
        callerAllowed || IpcTokenMatcher.matches(expectedToken, presentedToken)

    /**
     * Resolves the expected token from the module app's DataStore - the same source the
     * app publishes to - or null when unreadable/absent. [key] is the host's token pref key.
     */
    fun readExpectedToken(context: Context, key: String): String? = runCatching {
        runBlocking(Dispatchers.IO) {
            AppPreferencesDataStore.getString(
                context.applicationContext ?: context,
                key,
                "",
            )
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Token fallback for a hook process whose caller uid is not in the caller allowlist. */
    fun allowedByUri(context: Context?, uri: Uri?, expectedTokenKey: String): Boolean {
        if (context == null) return false
        val presented = presentedToken(uri) ?: return false
        return evaluate(
            callerAllowed = false,
            expectedToken = readExpectedToken(context, expectedTokenKey),
            presentedToken = presented,
        )
    }

    /** Token fallback for [android.content.ContentProvider.call] extras. */
    fun allowedByExtras(context: Context?, extras: Bundle?, expectedTokenKey: String): Boolean {
        if (context == null) return false
        val presented = presentedToken(extras) ?: return false
        return evaluate(
            callerAllowed = false,
            expectedToken = readExpectedToken(context, expectedTokenKey),
            presentedToken = presented,
        )
    }
}
