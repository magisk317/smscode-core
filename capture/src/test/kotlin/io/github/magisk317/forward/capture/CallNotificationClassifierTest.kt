package io.github.magisk317.forward.capture

import io.github.magisk317.forward.contract.constant.ForwardCallType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CallNotificationClassifierTest {

    @Test
    fun `classifies an incoming call from category and keywords`() {
        val classification = CallNotificationClassifier.classify(
            packageName = "com.example.dialer",
            category = "call",
            channelId = "calls",
            title = " incoming call ",
            body = "Someone is calling you",
            tickerText = "",
            expandedText = "",
        )

        assertNotNull(classification)
        assertEquals(ForwardCallType.INCOMING, classification!!.callType)
        assertEquals("来电", classification.label)
        assertEquals("ringing", classification.stage)
    }

    @Test
    fun `classifies missed call with ended stage`() {
        val classification = CallNotificationClassifier.classify(
            packageName = "com.example.dialer",
            category = "call",
            channelId = "calls",
            title = "Missed call",
            body = "You missed a call from +86 138 0013 8000",
            tickerText = "",
            expandedText = "",
        )

        assertNotNull(classification)
        assertEquals(ForwardCallType.MISSED, classification!!.callType)
        assertEquals("未接", classification.label)
        assertEquals("ended", classification.stage)
    }

    @Test
    fun `returns null for a plain notification`() {
        val classification = CallNotificationClassifier.classify(
            packageName = "com.example.shop",
            category = null,
            channelId = "promo",
            title = "Order shipped",
            body = "Your order has shipped",
            tickerText = "",
            expandedText = "",
        )

        assertNull(classification)
    }

    @Test
    fun `falls back to unknown type but keeps ongoing stage on call channel`() {
        val classification = CallNotificationClassifier.classify(
            packageName = "com.example.incallui",
            category = null,
            channelId = "ongoing_call",
            title = "InCall UI",
            body = "tap to return to the call",
            tickerText = "",
            expandedText = "",
        )

        assertNotNull(classification)
        assertEquals(ForwardCallType.UNKNOWN, classification!!.callType)
        assertEquals("ongoing", classification.stage)
        assertEquals(CallNotificationClassifier.DEFAULT_CALL_LABEL, classification.label)
    }

    @Test
    fun `display sender prefers the number when the title is generic`() {
        val sender = CallNotificationClassifier.resolveDisplaySender(
            title = "未接来电",
            body = "+86 138 0013 8000 called you",
            expandedText = "",
            tickerText = "",
        )

        assertEquals("13800138000", sender)
    }

    @Test
    fun `display sender keeps a specific title with digits`() {
        val sender = CallNotificationClassifier.resolveDisplaySender(
            title = "13800138000",
            body = "tap to return to call",
            expandedText = "",
            tickerText = "",
        )

        assertEquals("13800138000", sender)
    }

    @Test
    fun `normalized hint text lowercases and joins all views`() {
        val hint = CallNotificationClassifier.normalizedHintText(
            title = "Missed Call",
            body = "FROM +86",
            tickerText = "ticker",
            expandedText = "expanded",
        )

        assertEquals("missed call\nfrom +86\nticker\nexpanded", hint)
    }
}
