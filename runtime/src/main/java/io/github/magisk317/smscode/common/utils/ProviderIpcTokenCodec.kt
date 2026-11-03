package io.github.magisk317.smscode.common.utils

import android.net.Uri
import android.os.Bundle
import io.github.magisk317.xposed.runtime.RuntimeStateProviderClient
import io.github.magisk317.xposed.runtime.ipc.IpcTokenMatcher

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
}
