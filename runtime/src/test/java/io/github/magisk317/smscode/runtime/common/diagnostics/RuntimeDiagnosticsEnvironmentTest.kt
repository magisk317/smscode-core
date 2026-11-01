package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import io.github.magisk317.xposed.runtime.state.RuntimeDiagnosticsSnapshot
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.runtime.state.RuntimeStatusInputs
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeDiagnosticsEnvironmentTest {
    @AfterEach
    fun tearDown() {
        RuntimeStateStore.installActivationSource(
            runtimeConnectedProvider = { false },
            activationStatusResolver = null,
        )
    }

    @Test
    fun `install hands the live connection flag over to the kit store`() {
        RuntimeStateStore.installActivationSource(runtimeConnectedProvider = { false })
        assertFalse(RuntimeStateStore.isRuntimeConnected())

        RuntimeDiagnosticsEnvironment.install(
            RuntimeDiagnosticsConfig(runtimeConnectedProvider = { true }),
        )

        assertTrue(RuntimeStateStore.isRuntimeConnected())
    }

    @Test
    fun `install hands the activation rule over to the kit store`() {
        var called = false
        RuntimeDiagnosticsEnvironment.install(
            RuntimeDiagnosticsConfig(
                activationStatusResolver = { _, _, _ ->
                    called = true
                    true
                },
            ),
        )

        val resolver = RuntimeStateStore.activationStatusResolver
        assertTrue(resolver != null)
        val resolved = resolver?.invoke(
            mockk<Context>(relaxed = true),
            RuntimeDiagnosticsSnapshot(),
            RuntimeStatusInputs(false, false),
        )
        assertTrue(resolved == true)
        assertTrue(called)
    }
}
