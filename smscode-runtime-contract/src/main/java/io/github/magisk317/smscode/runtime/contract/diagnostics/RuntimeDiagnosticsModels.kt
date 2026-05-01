package io.github.magisk317.smscode.runtime.contract.diagnostics

data class ActivationDiagnosticsSnapshot(
    val lastServiceBindAtMs: Long = 0L,
    val lastServiceFrameworkName: String = "",
    val lastServiceFrameworkVersion: String = "",
    val lastHookAtMs: Long = 0L,
    val lastHookPackage: String = "",
    val lastHookProcess: String = "",
    val lastHookSource: String = "",
)

data class ActivationStatusInputs(
    val runtimeConnected: Boolean,
    val hasHookHeartbeat: Boolean,
    val hasLegacyActivationMarker: Boolean,
)

data class ActivationStatusState(
    val isEnabled: Boolean = false,
    val runtimeConnected: Boolean = false,
    val diagnostics: ActivationDiagnosticsSnapshot = ActivationDiagnosticsSnapshot(),
)
