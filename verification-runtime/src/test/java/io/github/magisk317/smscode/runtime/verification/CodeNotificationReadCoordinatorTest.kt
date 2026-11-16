package io.github.magisk317.smscode.runtime.verification

import android.app.Notification
import io.github.magisk317.xposed.logging.XLog
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodeNotificationReadCoordinatorTest {

    private val notification = mockk<Notification>(relaxed = true)
    private val readAction = mockk<Notification.Action>(relaxed = true)

    @BeforeEach
    fun silenceLogging() {
        XLog.setTestSink { _, _ -> }
    }

    @Test
    fun `non sms package is rejected without parsing`() {
        var parseCalls = 0
        var sent = 0
        val coordinator = CodeNotificationReadCoordinator(
            codeParser = { parseCalls++; "123456" },
            actionSender = { sent++ },
            readActionLookup = { readAction },
        )
        val decision = runBlocking {
            coordinator.maybeMarkAsRead("com.some.app", "title", "text", notification)
        }
        assertEquals(CodeNotificationReadCoordinator.Decision.REJECTED_PACKAGE, decision)
        assertEquals(0, parseCalls)
        assertEquals(0, sent)
    }

    @Test
    fun `no code in notification body leaves notification untouched`() {
        var lookupCalls = 0
        var sent = 0
        val coordinator = CodeNotificationReadCoordinator(
            codeParser = { null },
            actionSender = { sent++ },
            readActionLookup = { lookupCalls++; readAction },
        )
        val decision = runBlocking { coordinator.maybeMarkAsRead(
            TelephonySmsNotificationPackages.GOOGLE_MESSAGING,
            "Google Messages",
            "Your code is 654321",
            notification,
        ) }
        assertEquals(CodeNotificationReadCoordinator.Decision.CODE_NOT_DETECTED, decision)
        assertEquals(0, lookupCalls)
        assertEquals(0, sent)
    }

    @Test
    fun `code detected but poster exposes no read action is reported for vendor backlog`() {
        var sent = 0
        val coordinator = CodeNotificationReadCoordinator(
            codeParser = { "246810" },
            actionSender = { sent++ },
            readActionLookup = { null },
        )
        val decision = runBlocking { coordinator.maybeMarkAsRead(
            TelephonySmsNotificationPackages.MIUI_MMS,
            "短信",
            "【支付宝】验证码 246810",
            notification,
        ) }
        assertEquals(CodeNotificationReadCoordinator.Decision.NO_READ_ACTION, decision)
        assertEquals(0, sent)
    }

    @Test
    fun `matching notification triggers the read action exactly once`() {
        val sent = mutableListOf<Notification.Action>()
        val coordinator = CodeNotificationReadCoordinator(
            codeParser = { "135790" },
            actionSender = { sent.add(it) },
            readActionLookup = { readAction },
        )
        val decision = runBlocking { coordinator.maybeMarkAsRead(
            TelephonySmsNotificationPackages.GOOGLE_MESSAGING,
            "Google Messages",
            "G-135790 is your verification code",
            notification,
        ) }
        assertEquals(CodeNotificationReadCoordinator.Decision.MARK_AS_READ_SENT, decision)
        assertEquals(listOf(readAction), sent)
    }

    @Test
    fun `sender failure degrades to no read action instead of throwing`() {
        val coordinator = CodeNotificationReadCoordinator(
            codeParser = { "135790" },
            actionSender = { throw SecurityException("denied") },
            readActionLookup = { readAction },
        )
        val decision = runBlocking { coordinator.maybeMarkAsRead(
            TelephonySmsNotificationPackages.GOOGLE_MESSAGING,
            "Google Messages",
            "G-135790 is your verification code",
            notification,
        ) }
        assertEquals(CodeNotificationReadCoordinator.Decision.NO_READ_ACTION, decision)
    }
}
