package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.verification.SmsMessage
import io.github.magisk317.xposed.logging.XLog
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsParseActionRecoveryTest {
    @BeforeEach
    fun setUp() { XLog.setTestSink { _, _ -> } }

    @AfterEach
    fun tearDown() { XLog.setTestSink(null) }

    @Test
    fun `unavailable provider does not discard a live verification message`() {
        var prepared = 0
        val outcome = action(SmsDuplicateCheckResult.UNAVAILABLE) { prepared++; Message() }.parse()
        assertNotNull(outcome?.smsMsg)
        assertEquals(1, prepared)
    }

    @Test
    fun `known database duplicate never reaches the resolver`() {
        val outcome = action(SmsDuplicateCheckResult.DUPLICATE) { error("must not prepare") }.parse()
        assertTrue(outcome?.duplicated == true)
    }

    @Test
    fun `observer database record still permits remaining live actions`() {
        var prepared = 0
        val outcome = action(SmsDuplicateCheckResult.DUPLICATE, resume = true) { prepared++; Message() }.parse()
        assertNotNull(outcome?.smsMsg)
        assertEquals(1, prepared)
    }

    @Test
    fun `no matching verification code is not treated as successful handling`() {
        assertNull(action(SmsDuplicateCheckResult.UNAVAILABLE) { null }.parse())
    }

    private fun action(
        result: SmsDuplicateCheckResult,
        resume: Boolean = false,
        resolve: () -> Message?,
    ) = SmsParseAction(
        pluginContext = mockk<Context>(),
        phoneContext = mockk<Context>(),
        smsIntent = mockk<Intent>(),
        deduplicateEnabled = true,
        incomingSmsParser = { Message() },
        duplicateChecker = { _, _, _ -> result },
        preparedSmsResolver = { _, _, _, _, _ -> resolve() },
        resumeLiveActions = { _, _, _ -> resume },
    )

    private class Message : SmsMessage, Parcelable {
        override val sender = "test"
        override val body = "test message"
        override val date = 123L
        override val smsCode = "123456"
        override val packageName: String? = null
        override val company: String? = null
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }
}
