package io.github.magisk317.smscode.runtime.verification

import io.github.magisk317.smscode.verification.SmsMessage

import android.app.NotificationManager
import android.content.Context
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Guards the diagnostics precedence rules of [NotifyActionHelper].
 *
 * Both rules exist because the phone-owned notification is posted by a different package than the
 * module app: an inspection resolved against the module app answers a question nobody asked, and an
 * explicit decision from the caller must not be overridden by a bridge that inspects the wrong
 * package.
 */
class NotifyActionHelperDiagnosticsTest {

    @Test
    fun run_inspectsTheDiagnosticsContextRatherThanThePluginContext() {
        val inspected = mutableListOf<Context>()
        val bridge = mockk<NotificationPlatformBridge>()
        every { bridge.inspectDelivery(any(), any()) } answers {
            inspected += firstArg<Context>()
            canPost()
        }

        val result = helper(
            diagnostics = null,
            diagnosticsContext = phoneContext,
            notificationBridge = bridge,
            channelId = CHANNEL_ID,
        ).run()

        assertEquals("notified", result)
        assertEquals(listOf<Context>(phoneContext), inspected)
    }

    @Test
    fun run_prefersTheExplicitDiagnosticsOverTheBridge() {
        val bridge = mockk<NotificationPlatformBridge>(relaxed = true)

        val result = helper(
            diagnostics = {
                NotifyActionHelper.DeliveryDiagnostics(canPost = false, summary = "blocked")
            },
            notificationBridge = bridge,
            channelId = CHANNEL_ID,
        ).run()

        assertNull(result)
        verify(exactly = 0) { bridge.inspectDelivery(any(), any()) }
    }

    @Test
    fun run_skipsWhenNoDiagnosticsSourceAllowsPosting() {
        val result = helper(
            diagnostics = {
                NotifyActionHelper.DeliveryDiagnostics(canPost = false, summary = "phone=none")
            },
            channelId = CHANNEL_ID,
        ).run()

        assertNull(result)
    }

    @Test
    fun run_keepsPostingPossibleWhenNoDiagnosticsSourceIsAvailable() {
        val result = helper(diagnostics = null).run()

        assertEquals("notified", result)
    }

    private fun helper(
        diagnostics: ((Context) -> NotifyActionHelper.DeliveryDiagnostics)?,
        diagnosticsContext: Context? = null,
        notificationBridge: NotificationPlatformBridge? = null,
        channelId: String = "",
    ): NotifyActionHelper<TestSmsMessage, String> = NotifyActionHelper(
        pluginContext = pluginContext,
        smsMsg = TestSmsMessage,
        enabled = true,
        autoCancelEnabledProvider = { false },
        retentionTimeMsProvider = { 0L },
        channelId = channelId,
        diagnostics = diagnostics,
        diagnosticsContext = diagnosticsContext,
        notificationBridge = notificationBridge,
        notifier = { "notified" },
    )

    private object TestSmsMessage : SmsMessage {
        override val sender: String? = "sender"
        override val body: String? = "body"
        override val date: Long = 0L
        override val company: String? = "company"
        override val smsCode: String? = "123456"
        override val packageName: String? = null
    }

    private companion object {
        const val CHANNEL_ID = "smscode_notification"
        val pluginContext: Context = mockk(relaxed = true)
        val phoneContext: Context = mockk(relaxed = true)

        fun canPost() = NotificationDeliveryDiagnostics(
            notificationsEnabled = true,
            postNotificationsGranted = true,
            channelImportance = NotificationManager.IMPORTANCE_HIGH,
        )
    }
}
