package io.github.magisk317.smscode.runtime.verification

import android.content.Context

interface VerificationRuntimeContext {
    val pluginContext: Context
    val phoneContext: Context
}
