package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsHookConstructorInitializerTest {

    private data class Runtime(
        override val phoneContext: Context,
        override val pluginContext: Context,
    ) : VerificationRuntimeContext

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun handle_stopsWhenRuntimeUnavailable() {
        stubXLog()
        val phoneContext = mockk<Context>(relaxed = true)
        var activationCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { null },
            conflictNoticeChannelInitializer = { _, _ -> },
            conflictSuppressor = { _, _ -> false },
            showNotificationReader = { true },
            activationMarker = { activationCalls += 1 },
        )

        val outcome = initializer.handle(phoneContext)

        assertEquals(SmsHookConstructorInitializer.StopReason.RUNTIME_UNAVAILABLE, outcome.stopReason)
        assertFalse(outcome.initialized)
        // Nothing may run before the runtime exists, least of all the activation mark.
        assertEquals(0, activationCalls)
    }

    @Test
    fun handle_registersNotificationAndObserverWhenHealthy() {
        stubXLog()
        val runtime = runtime()
        var conflictChannelCalls = 0
        var notificationChannelCalls = 0
        var copyReceiverCalls = 0
        var activationContext: Context? = null
        var heartbeatSource: String? = null
        var observerCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { runtime },
            conflictNoticeChannelInitializer = { _, _ -> conflictChannelCalls += 1 },
            conflictSuppressor = { _, _ -> false },
            showNotificationReader = { true },
            notificationChannelInitializer = { notificationChannelCalls += 1 },
            copyCodeRegistrar = { copyReceiverCalls += 1 },
            activationMarker = { context -> activationContext = context },
            heartbeatRecorder = { source -> heartbeatSource = source },
            inboxObserverRegistrar = { observerCalls += 1 },
        )

        val outcome = initializer.handle(runtime.phoneContext)

        assertNull(outcome.stopReason)
        assertFalse(outcome.suppressedByRelay)
        assertEquals(1, conflictChannelCalls)
        assertEquals(1, notificationChannelCalls)
        assertEquals(1, copyReceiverCalls)
        assertEquals(runtime.pluginContext, activationContext)
        assertEquals("sms_handler_constructor", heartbeatSource)
        assertEquals(1, observerCalls)
    }

    @Test
    fun handle_suppressedConstructorSkipsReceiverAndRegistersSuppression() {
        stubXLog()
        val runtime = runtime()
        var copyReceiverCalls = 0
        var suppressionStage: String? = null
        var observerCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { runtime },
            conflictNoticeChannelInitializer = { _, _ -> },
            conflictSuppressor = { _, _ -> true },
            showNotificationReader = { true },
            notificationChannelInitializer = {},
            copyCodeRegistrar = { copyReceiverCalls += 1 },
            activationMarker = {},
            heartbeatRecorder = {},
            suppressionLogger = { stage -> suppressionStage = stage },
            inboxObserverRegistrar = { observerCalls += 1 },
        )

        val outcome = initializer.handle(runtime.phoneContext)

        assertTrue(outcome.initialized)
        assertTrue(outcome.suppressedByRelay)
        // Another module owns the SMS, so we must not also copy the code.
        assertEquals(0, copyReceiverCalls)
        assertEquals("constructor", suppressionStage)
        assertEquals(0, observerCalls)
    }

    @Test
    fun handle_skipsReceiverWhenNotificationsDisabled() {
        stubXLog()
        val runtime = runtime()
        var notificationChannelCalls = 0
        var copyReceiverCalls = 0
        var observerCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { runtime },
            conflictNoticeChannelInitializer = { _, _ -> },
            conflictSuppressor = { _, _ -> false },
            showNotificationReader = { false },
            notificationChannelInitializer = { notificationChannelCalls += 1 },
            copyCodeRegistrar = { copyReceiverCalls += 1 },
            activationMarker = {},
            heartbeatRecorder = {},
            inboxObserverRegistrar = { observerCalls += 1 },
        )

        val outcome = initializer.handle(runtime.phoneContext)

        assertTrue(outcome.initialized)
        assertEquals(0, notificationChannelCalls)
        assertEquals(0, copyReceiverCalls)
        // The observer is independent of the notification setting.
        assertEquals(1, observerCalls)
    }

    private fun runtime(): Runtime = Runtime(
        pluginContext = mockk<Context>(relaxed = true),
        phoneContext = mockk<Context>(relaxed = true),
    )

    private fun stubXLog() {
        XLog.setTestSink { _, _ -> }
    }
}
