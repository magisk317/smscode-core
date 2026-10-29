package io.github.magisk317.smscode.runtime.common.diagnostics

import io.github.magisk317.smscode.runtime.contract.diagnostics.CallNotificationSourceSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.HookInstallStatus
import io.github.magisk317.smscode.runtime.contract.diagnostics.HookProcessStatus
import io.github.magisk317.smscode.runtime.contract.diagnostics.NotifyEventSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivationDiagnosticsCodecTest {

    @Test
    fun `hook process key round trips dotted package and process names`() {
        val key = ActivationDiagnosticsCodec.hookProcessKey(
            packageName = "com.android.phone",
            processName = "com.android.phone:remote",
        )
        assertTrue(key.startsWith(ActivationDiagnosticsCodec.HOOK_PROCESS_KEY_PREFIX))
        assertEquals(
            "com.android.phone" to "com.android.phone:remote",
            ActivationDiagnosticsCodec.decodeHookProcessKey(key),
        )
    }

    @Test
    fun `malformed hook process keys are rejected`() {
        assertNull(ActivationDiagnosticsCodec.decodeHookProcessKey("last_hook_at_ms"))
        assertNull(ActivationDiagnosticsCodec.decodeHookProcessKey("hook_process."))
        assertNull(ActivationDiagnosticsCodec.decodeHookProcessKey("hook_process.onlyone"))
    }

    @Test
    fun `hook process value round trips`() {
        val status = HookProcessStatus(
            packageName = "android",
            processName = "system",
            lastSeenAtMs = 1_793_000_000_000L,
            source = "process|online",
            moduleVersion = 55,
        )
        val key = ActivationDiagnosticsCodec.hookProcessKey(status.packageName, status.processName)
        val decoded = ActivationDiagnosticsCodec.decodeHookProcess(
            key,
            ActivationDiagnosticsCodec.encodeHookProcess(status),
        )
        assertNotNull(decoded)
        assertEquals(status.packageName, decoded!!.packageName)
        assertEquals(status.processName, decoded.processName)
        assertEquals(status.lastSeenAtMs, decoded.lastSeenAtMs)
        assertEquals(status.moduleVersion, decoded.moduleVersion)
        assertEquals("process/online", decoded.source)
    }

    @Test
    fun `process trimming keeps the newest entries`() {
        val entries = (1..20).map { index ->
            HookProcessStatus(
                packageName = "pkg$index",
                processName = "pkg$index",
                lastSeenAtMs = index.toLong(),
            )
        }
        val trimmed = ActivationDiagnosticsCodec.trimProcesses(entries)
        assertEquals(ActivationDiagnosticsCodec.MAX_HOOK_PROCESSES, trimmed.size)
        assertEquals(20L, trimmed.first().lastSeenAtMs)
        assertEquals(5L, trimmed.last().lastSeenAtMs)
    }

    @Test
    fun `call source round trips`() {
        val snapshot = CallNotificationSourceSnapshot(
            packageName = "com.android.server.telecom",
            channelId = "TelecomIncomingCalls",
            category = "call",
            lastSeenAtMs = 42L,
        )
        assertEquals(
            snapshot,
            ActivationDiagnosticsCodec.decodeCallSource(
                ActivationDiagnosticsCodec.encodeCallSource(snapshot),
            ),
        )
    }

    @Test
    fun `empty call source survives a round trip`() {
        assertEquals(
            CallNotificationSourceSnapshot(),
            ActivationDiagnosticsCodec.decodeCallSource(
                ActivationDiagnosticsCodec.encodeCallSource(CallNotificationSourceSnapshot()),
            ),
        )
    }

    @Test
    fun `hook install entry round trips`() {
        val status = HookInstallStatus(
            hook = "nms",
            status = "installed",
            packageName = "android",
            processName = "system",
            detail = "enqueueNotificationInternal",
            lastSeenAtMs = 99L,
        )
        val key = ActivationDiagnosticsCodec.hookInstallKey(status.hook)
        assertEquals("nms", ActivationDiagnosticsCodec.decodeHookInstallKey(key))
        assertEquals(
            status,
            ActivationDiagnosticsCodec.decodeHookInstall(
                key,
                ActivationDiagnosticsCodec.encodeHookInstall(status),
            ),
        )
    }

    @Test
    fun `notify event round trips`() {
        val event = NotifyEventSnapshot(
            source = "nms",
            packageName = "com.android.server.telecom",
            channelId = "TelecomIncomingCalls",
            msgType = "call_notify",
            skipReason = "group_summary",
            lastSeenAtMs = 7L,
        )
        assertEquals(
            event,
            ActivationDiagnosticsCodec.decodeNotifyEvent(
                ActivationDiagnosticsCodec.encodeNotifyEvent(event),
            ),
        )
    }
}
