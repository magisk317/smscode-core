package io.github.magisk317.smscode.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

abstract class AsyncActionBroadcastReceiver : BroadcastReceiver() {
    protected abstract val action: String

    final override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return
        val pendingResult = goAsync()
        handle(context.applicationContext ?: context, intent) {
            pendingResult.finish()
        }
    }

    protected abstract fun handle(
        context: Context,
        intent: Intent,
        onComplete: () -> Unit,
    )
}
