package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context

class RuntimeDiagnosticsInstaller(
    private val install: (RuntimeDiagnosticsConfig) -> Unit = RuntimeDiagnosticsEnvironment::install,
    private val configProvider: () -> RuntimeDiagnosticsConfig,
) {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            install(configProvider())
            installed = true
        }
    }
}

object RuntimeDiagnosticsPreferences {
    fun readInt(
        context: Context,
        preferencesName: String,
        key: String,
        defaultValue: Int,
        minimumValue: Int = Int.MIN_VALUE,
        maximumValue: Int = Int.MAX_VALUE,
    ): Int {
        require(minimumValue <= maximumValue)
        val preferences = runCatching {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }.getOrNull() ?: return defaultValue.coerceIn(minimumValue, maximumValue)
        val value = when (val raw = preferences.all[key]) {
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: defaultValue
        return value.coerceIn(minimumValue, maximumValue)
    }
}
