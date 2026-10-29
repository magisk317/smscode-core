package io.github.magisk317.forward.capture

import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.xposed.capability.prefs.CorePrefs
import io.github.magisk317.xposed.capability.prefs.CorePrefsAccess
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardDispatchPrefsTest {

    @AfterEach
    fun tearDown() {
        CorePrefs.resetForTest()
    }

    @Test
    fun `reads ipc token from core prefs`() {
        installCorePrefs(stringValues = mapOf(ForwardNotificationDispatcher.IPC_TOKEN_PREF_KEY to "ipc-token-from-core"))

        val dispatcher = ForwardNotificationDispatcher(
            recoveryServiceClassName = null,
            relaunchActivityClassName = null,
        )

        assertEquals("ipc-token-from-core", dispatcher.readIpcToken())
    }

    @Test
    fun `falls back to empty token when core prefs is unset`() {
        installCorePrefs()

        val dispatcher = ForwardNotificationDispatcher(
            recoveryServiceClassName = null,
            relaunchActivityClassName = null,
        )

        assertEquals("", dispatcher.readIpcToken())
    }

    @Test
    fun `force stop recovery reads its pref key`() {
        installCorePrefs(booleanValues = mapOf(ForwardNotificationDispatcher.KEY_FORCE_STOP_RECOVERY to true))

        val dispatcher = ForwardNotificationDispatcher(
            recoveryServiceClassName = null,
            relaunchActivityClassName = null,
        )

        assertTrue(dispatcher.isForceStopRecoveryEnabled("caller.pkg"))
    }

    @Test
    fun `force stop recovery defaults to disabled`() {
        installCorePrefs()

        val dispatcher = ForwardNotificationDispatcher(
            recoveryServiceClassName = null,
            relaunchActivityClassName = null,
        )

        assertFalse(dispatcher.isForceStopRecoveryEnabled("caller.pkg"))
    }

    @Test
    fun `wake extra shares the ipc token key`() {
        assertEquals(ForwardBroadcastContract.EXTRA_IPC_TOKEN, ForwardNotificationDispatcher.IPC_TOKEN_PREF_KEY)
    }

    @Test
    fun `namespace actions append the stable suffixes`() {
        assertEquals("io.github.magisk317.relay.ACTION_FORWARD_SMS", ForwardBroadcastContract.forwardAction("io.github.magisk317.relay"))
        assertEquals(
            "io.github.magisk317.relay.FORCE_STOP_RECOVERY_WAKEUP",
            ForwardBroadcastContract.recoveryWakeupAction("io.github.magisk317.relay"),
        )
    }

    private fun installCorePrefs(
        booleanValues: Map<String, Boolean> = emptyMap(),
        stringValues: Map<String, String> = emptyMap(),
        intValues: Map<String, Int> = emptyMap(),
    ) {
        CorePrefs.install(
            object : CorePrefsAccess {
                override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
                    booleanValues[key] ?: defaultValue

                override fun getString(key: String, defaultValue: String): String =
                    stringValues[key] ?: defaultValue

                override fun getInt(key: String, defaultValue: Int): Int =
                    intValues[key] ?: defaultValue
            },
        )
    }
}
