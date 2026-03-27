package io.github.magisk317.smscode.verification

import android.content.Context

interface VerificationRuntimeContext {
    val pluginContext: Context
    val phoneContext: Context
}
