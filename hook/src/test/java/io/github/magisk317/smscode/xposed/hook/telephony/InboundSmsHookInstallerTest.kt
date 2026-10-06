package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.BroadcastReceiver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class InboundSmsHookInstallerTest {

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    class Sample {
        fun noReceiverFlag(): Boolean = true
        fun receiverLast(context: android.content.Context, intent: android.content.Intent, r: Receiver) = 0
        fun receiverMiddle(context: android.content.Context, r: Receiver, intent: android.content.Intent) = 0
        fun noReceiver(context: android.content.Context, intent: android.content.Intent) = 0
    }

    private fun method(name: String, vararg params: Class<*>): Method =
        Sample::class.java.getDeclaredMethod(name, *params)

    @Test
    fun receiverArgumentIndex_findsTrailingReceiver() {
        assertEquals(
            2,
            method(
                "receiverLast",
                android.content.Context::class.java,
                android.content.Intent::class.java,
                Receiver::class.java,
            ).receiverArgumentIndex(),
        )
    }

    @Test
    fun receiverArgumentIndex_findsMiddleReceiver() {
        // dispatchIntent moved its parameters between Android versions, so the index is
        // resolved per method rather than hard-coded.
        assertEquals(
            1,
            method(
                "receiverMiddle",
                android.content.Context::class.java,
                Receiver::class.java,
                android.content.Intent::class.java,
            ).receiverArgumentIndex(),
        )
    }

    @Test
    fun receiverArgumentIndex_returnsMinusOneWhenAbsent() {
        assertEquals(
            -1,
            method(
                "noReceiver",
                android.content.Context::class.java,
                android.content.Intent::class.java,
            ).receiverArgumentIndex(),
        )
        assertEquals(-1, method("noReceiverFlag").receiverArgumentIndex())
    }

    @Test
    fun dispatcherMethodNames_coverBothHandlerAndController() {
        val handler = InboundSmsHookInstaller.SMS_DISPATCHER_METHODS
        val controller = InboundSmsHookInstaller.SMS_DISPATCHER_CONTROLLER_METHODS
        assertTrue(handler.contains("dispatchSmsDeliveryIntent"))
        // The controller adds one extra variant; the handler set must stay a subset.
        assertTrue(controller.containsAll(handler))
        assertEquals(
            "dispatchSmsDeliveryIntentToAppWithPermission",
            controller.single { it !in handler },
        )
    }

    @Test
    fun classNames_targetTheTelephonyClasses() {
        assertEquals(
            "com.android.internal.telephony.InboundSmsHandler",
            InboundSmsHookInstaller.SMS_HANDLER_CLASS,
        )
        assertEquals(
            "com.android.internal.telephony.SmsDispatchersController",
            InboundSmsHookInstaller.SMS_DISPATCHERS_CONTROLLER_CLASS,
        )
    }
}
