package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import io.github.magisk317.smscode.verification.SmsMessage
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDispatchGuardTest {
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun stableKeyAndId_ignoreRoutingMetadataDifferences() {
        val first = message(packageName = "com.bank", company = "Bank", simSlot = 0, subId = 1)
        val second = message(packageName = "com.other", company = "Other", simSlot = 1, subId = 2)

        assertEquals(
            NotificationDispatchGuard.buildNotificationKey(first),
            NotificationDispatchGuard.buildNotificationKey(second),
        )
        assertEquals(
            NotificationDispatchGuard.stableNotificationId(first),
            NotificationDispatchGuard.stableNotificationId(second),
        )
    }

    @Test
    fun claim_rejectsRepeatedKeyWithinGuardWindow() {
        val claimedKeys = mutableSetOf<String>()
        val first = message()
        val second = message(packageName = "com.other", simSlot = 1, subId = 2)

        fun claim(message: SmsMessage): Boolean = NotificationDispatchGuard.claim(context, message) { _, _, keys, _, _ ->
            NotificationDispatchGuard.ClaimResult(claimed = keys.any(claimedKeys::add), ageMs = 120L)
        }

        assertTrue(claim(first))
        assertFalse(claim(second))
    }

    @Test
    fun differentMessageFingerprint_isNotSuppressed() {
        val claimedKeys = mutableSetOf<String>()
        val first = message()
        val differentBody = message(body = "otp 654321")
        val differentDate = message(date = 2_000L)

        fun claim(message: SmsMessage): Boolean = NotificationDispatchGuard.claim(context, message) { _, _, keys, _, _ ->
            NotificationDispatchGuard.ClaimResult(claimed = keys.any(claimedKeys::add))
        }

        assertTrue(claim(first))
        assertTrue(claim(differentBody))
        assertTrue(claim(differentDate))
        assertNotEquals(
            NotificationDispatchGuard.buildNotificationKey(first),
            NotificationDispatchGuard.buildNotificationKey(differentBody),
        )
    }

    private fun message(
        sender: String = "1068",
        body: String = "otp 123456",
        code: String = "123456",
        date: Long = 1_000L,
        packageName: String? = "com.bank",
        company: String? = "Bank",
        simSlot: Int = 0,
        subId: Int = 1,
    ): SmsMessage = object : SmsMessage {
        override val sender = sender
        override val body = body
        override val date = date
        override val smsCode = code
        override val packageName = packageName
        override val company = company
        override val simSlot = simSlot
        override val subId = subId
    }
}
