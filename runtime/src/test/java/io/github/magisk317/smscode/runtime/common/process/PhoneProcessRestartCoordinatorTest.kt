package io.github.magisk317.smscode.runtime.common.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneProcessRestartCoordinatorTest {
    @Test
    fun `parses restart counters and rejects incomplete output`() {
        assertEquals(
            PhoneProcessRestartCoordinator.RestartCommandSummary(found = 3, killed = 2, failed = 1),
            PhoneProcessRestartCoordinator.parseRestartCommandSummary("found=3 killed=2 failed=1\n"),
        )
        assertNull(PhoneProcessRestartCoordinator.parseRestartCommandSummary("found=2 killed=2"))
    }

    @Test
    fun `marks install handled only for parsed successful command`() {
        assertTrue(
            PhoneProcessRestartCoordinator.RestartAttemptResult(
                exitCode = 0,
                summary = PhoneProcessRestartCoordinator.RestartCommandSummary(1, 1, 0),
                rawOutput = "",
            ).shouldMarkInstallHandled,
        )
        assertFalse(
            PhoneProcessRestartCoordinator.RestartAttemptResult(
                exitCode = 0,
                summary = PhoneProcessRestartCoordinator.RestartCommandSummary(1, 0, 1),
                rawOutput = "",
            ).shouldMarkInstallHandled,
        )
    }

    @Test
    fun `restart command shell quotes configured process names`() {
        val command = PhoneProcessRestartCoordinator.buildRestartCommand(listOf("normal", "bad'name"))

        assertTrue(command.contains("'normal'"))
        assertTrue(command.contains("'bad'\\''name'"))
    }
}
