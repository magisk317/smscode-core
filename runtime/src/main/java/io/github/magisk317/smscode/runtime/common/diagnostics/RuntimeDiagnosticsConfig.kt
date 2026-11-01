package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.xposed.runtime.state.RuntimeDiagnosticsSnapshot
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.runtime.state.RuntimeStatusInputs
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
    val activationStatusResolver: ((Context, RuntimeDiagnosticsSnapshot, RuntimeStatusInputs) -> Boolean)? = null,
    val routeResolver: ((String?) -> String)? = null,
)

object RuntimeDiagnosticsEnvironment {
    @Volatile
    private var config: RuntimeDiagnosticsConfig = RuntimeDiagnosticsConfig()

    fun install(newConfig: RuntimeDiagnosticsConfig) {
        config = newConfig
        // The kit store owns the activation status. Without this hand-over it could only fall back
        // to what the hook wrote into the app, which is exactly the signal that disappears when the
        // system refuses to start the app's content provider.
        RuntimeStateStore.installActivationSource(
            runtimeConnectedProvider = newConfig.runtimeConnectedProvider,
            activationStatusResolver = newConfig.activationStatusResolver,
        )
    }

    fun current(): RuntimeDiagnosticsConfig = config
}
