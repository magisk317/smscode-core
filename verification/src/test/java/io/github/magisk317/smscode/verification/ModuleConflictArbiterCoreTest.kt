package io.github.magisk317.smscode.verification

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleConflictArbiterCoreTest {
    @Test
    fun shouldSuppress_returnsFalseForNullContext() {
        var checkCount = 0
        val arbiter = arbiter(
            packageInstalledChecker = { _, _ ->
                checkCount += 1
                true
            },
        )

        assertFalse(arbiter.shouldSuppress(null, "test"))
        assertEquals(0, checkCount)
    }

    @Test
    fun shouldSuppress_bypassFlagSkipsPackageCheck() {
        val context = mockk<Context>(relaxed = true)
        var checkCount = 0
        val arbiter = arbiter(
            allowConflictBypass = true,
            packageInstalledChecker = { _, _ ->
                checkCount += 1
                true
            },
        )

        assertFalse(arbiter.shouldSuppress(context, "test"))
        assertEquals(0, checkCount)
    }

    @Test
    fun shouldSuppress_cachesPackageInstalledDecision() {
        val context = mockk<Context>(relaxed = true)
        var checkCount = 0
        val arbiter = arbiter(
            packageInstalledChecker = { _, packageName ->
                checkCount += 1
                packageName == TARGET_PACKAGE
            },
        )

        assertTrue(arbiter.shouldSuppress(context, "first"))
        assertTrue(arbiter.shouldSuppress(context, "second"))
        assertEquals(1, checkCount)
    }

    private fun arbiter(
        allowConflictBypass: Boolean = false,
        packageInstalledChecker: (Context, String) -> Boolean,
    ): ModuleConflictArbiterCore {
        return ModuleConflictArbiterCore(
            targetPackage = TARGET_PACKAGE,
            allowConflictBypass = { allowConflictBypass },
            packageInstalledChecker = packageInstalledChecker,
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.magisk317.relay"
    }
}
