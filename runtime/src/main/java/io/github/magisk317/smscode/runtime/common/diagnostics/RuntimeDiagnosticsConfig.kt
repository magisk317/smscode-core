package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusInputs

data class RuntimeDiagnosticsConfig(
    val applicationId: String = "",
    val logTag: String = "runtime-common",
    val exportFilePrefix: String = "runtime_logs_",
    val stagingDirPrefix: String = ".tmp_runtime_logs_",
    val logRetentionDaysProvider: ((Context) -> Int)? = null,
    val runtimeConnectedProvider: () -> Boolean = { false },
    val activationStatusResolver: ((Context, ActivationDiagnosticsSnapshot, ActivationStatusInputs) -> Boolean)? = null,
    val routeResolver: ((String?) -> String)? = null,
)

object RuntimeDiagnosticsEnvironment {
    @Volatile
    private var config: RuntimeDiagnosticsConfig = RuntimeDiagnosticsConfig()

    fun install(newConfig: RuntimeDiagnosticsConfig) {
        config = newConfig
    }

    fun current(): RuntimeDiagnosticsConfig = config
}
