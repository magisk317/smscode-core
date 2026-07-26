package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel

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
            runCatching {
                install(configProvider())
                installed = true
                MagiskOtel.event(
                    name = "app.monitor",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "diagnostics_install",
                        "reason" to "installed",
                    ),
                    statusOk = true,
                )
            }.onFailure { error ->
                MagiskOtel.event(
                    name = "app.monitor",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "diagnostics_install",
                        "reason" to "install_failed",
                        "error_class" to error.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
                throw error
            }
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
