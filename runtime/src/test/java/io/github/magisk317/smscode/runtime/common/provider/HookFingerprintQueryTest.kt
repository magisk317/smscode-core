package io.github.magisk317.smscode.runtime.common.provider

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HookFingerprintQueryTest {
    private val resolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { contentResolver } returns resolver
    }
    private val uri = mockk<Uri> {
        every { authority } returns "example.db.provider"
    }

    @Test
    fun `successful queries preserve duplicate results and close the cursor without reporting failure`() {
        val diagnostics = mutableListOf<HookFingerprintQuery.Unavailable>()
        val cursor = mockk<Cursor>(relaxed = true)
        every { resolver.query(uri, any(), any(), any(), null) } returns cursor

        for (duplicate in listOf(false, true)) {
            every { cursor.moveToFirst() } returns duplicate
            val result = query(onUnavailable = diagnostics::add)
            assertEquals(
                if (duplicate) SmsDuplicateCheckResult.DUPLICATE else SmsDuplicateCheckResult.NOT_DUPLICATE,
                result,
            )
        }

        assertTrue(diagnostics.isEmpty())
        verify(exactly = 2) { cursor.close() }
    }

    @Test
    fun `null cursor reports one structured failure without invoking the exception callback`() {
        every { resolver.query(uri, any(), any(), any(), null) } returns null
        val diagnostics = mutableListOf<HookFingerprintQuery.Unavailable>()
        var legacyCalls = 0

        assertEquals(
            SmsDuplicateCheckResult.UNAVAILABLE,
            query(onFailure = { legacyCalls++ }, onUnavailable = diagnostics::add),
        )

        assertEquals(0, legacyCalls)
        val failure = diagnostics.single()
        assertEquals(HookFingerprintQuery.UnavailableReason.NULL_CURSOR, failure.reason)
        assertEquals("example.db.provider", failure.authority)
        assertTrue(failure.elapsedMs >= 0L)
        assertNull(failure.exceptionClass)
    }

    @Test
    fun `exception reports only class and authority and does not double report through legacy callback`() {
        val secret = "private-query-token-and-sms-content"
        every { resolver.query(uri, any(), any(), any(), null) } throws SecurityException(secret)
        val diagnostics = mutableListOf<HookFingerprintQuery.Unavailable>()
        var legacyCalls = 0

        assertEquals(
            SmsDuplicateCheckResult.UNAVAILABLE,
            query(onFailure = { legacyCalls++ }, onUnavailable = diagnostics::add),
        )

        assertEquals(0, legacyCalls)
        val failure = diagnostics.single()
        assertEquals(HookFingerprintQuery.UnavailableReason.EXCEPTION, failure.reason)
        assertEquals(SecurityException::class.java.name, failure.exceptionClass)
        assertEquals("example.db.provider", failure.authority)
        assertTrue(failure.elapsedMs >= 0L)
        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.toLogMessage().contains(secret))
            // The recorded call must stay the lambda's last expression (mockk), so the Unit
    // coercion diagnostic cannot be shaped away: statement form warns NOT_USED, tail
    // form warns COERCION. Targeted suppress on this test only.
    @Suppress("RETURN_VALUE_NOT_USED_COERCION")
    verify(exactly = 0) { uri.toString() }
    }

    @Test
    fun `cursor failure closes cursor and reports exception once`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { resolver.query(uri, any(), any(), any(), null) } returns cursor
        every { cursor.moveToFirst() } throws IllegalStateException("private cursor details")
        val diagnostics = mutableListOf<HookFingerprintQuery.Unavailable>()

        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, query(onUnavailable = diagnostics::add))
        assertEquals(IllegalStateException::class.java.name, diagnostics.single().exceptionClass)
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun `diagnostic callback failure cannot escape or cause a second report`() {
        every { resolver.query(uri, any(), any(), any(), null) } returns null
        var reports = 0

        val result = query(onUnavailable = {
            reports++
            throw IllegalStateException("logger unavailable")
        })

        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, result)
        assertEquals(1, reports)
        verify(exactly = 1) { resolver.query(uri, any(), any(), any(), null) }
    }

    @Test
    fun `legacy exception callback remains supported when structured callback is absent`() {
        val error = IllegalArgumentException("legacy failure")
        every { resolver.query(uri, any(), any(), any(), null) } throws error
        var reported: Throwable? = null

        assertEquals(SmsDuplicateCheckResult.UNAVAILABLE, query(onFailure = { reported = it }))
        assertSame(error, reported)
    }

    @Test
    fun `diagnostic log retains reason and error within the hook logger limit`() {
        val failure = HookFingerprintQuery.Unavailable(
            reason = HookFingerprintQuery.UnavailableReason.NULL_CURSOR,
            authority = "a".repeat(256),
            elapsedMs = Long.MAX_VALUE,
            exceptionClass = "example." + "Exception".repeat(40),
        )
        val message = failure.toLogMessage()

        assertTrue(message.length <= 190)
        assertTrue(message.contains("reason=null_cursor"))
        assertTrue(message.contains("error=Exception"))
    }

    private fun query(
        onFailure: ((Throwable) -> Unit)? = null,
        onUnavailable: ((HookFingerprintQuery.Unavailable) -> Unit)? = null,
    ): SmsDuplicateCheckResult = HookFingerprintQuery.query(
        context = context,
        uri = uri,
        sender = "sender",
        body = "body",
        date = 123L,
        msgType = 1,
        onFailure = onFailure,
        onUnavailable = onUnavailable,
    )
}
