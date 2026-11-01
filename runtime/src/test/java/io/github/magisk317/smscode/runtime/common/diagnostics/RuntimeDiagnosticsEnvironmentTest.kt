package io.github.magisk317.smscode.runtime.common.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeDiagnosticsEnvironmentTest {

    @Test
    fun `the installed configuration carries the module id the provider is addressed by`() {
        RuntimeDiagnosticsEnvironment.install(
            RuntimeDiagnosticsConfig(applicationId = "io.github.magisk317.smscode"),
        )

        // The hook process carries another package's context, so the module id is the only way to
        // address the module's own provider.
        assertEquals(
            "io.github.magisk317.smscode",
            RuntimeDiagnosticsEnvironment.current().applicationId,
        )
    }
}
