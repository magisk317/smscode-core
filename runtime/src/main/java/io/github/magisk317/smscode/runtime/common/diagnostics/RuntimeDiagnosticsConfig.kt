package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusInputs
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleIdentity
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames

/**
 * Host-supplied wiring for the shared runtime diagnostics.
 *
 * [product] is the one token a host has to choose: bundle name, staging directory and private
 * export directory are derived from it, which is what kept two sibling apps from naming the
 * same archive differently in the save dialog and on disk.
 */
data class RuntimeDiagnosticsConfig(
    val applicationId: String = "",
    val logTag: String = "runtime-common",
    val product: String = "runtime",
    val exportFilePrefix: String = DiagnosticBundleNames.exportFilePrefix(product),
    val stagingDirPrefix: String = DiagnosticBundleNames.stagingDirPrefix(product),
    val privateExportDirName: String = DiagnosticBundleNames.privateExportDirName(product),
    val distributionChannel: String = DiagnosticBundleIdentity.UNKNOWN,
    val commitHash: String = DiagnosticBundleIdentity.UNKNOWN,
    val libraryCommitShas: Map<String, String> = emptyMap(),
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
