package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsBlockEvaluatorTest {

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun evaluate_ignoresNonSmsAction() {
        stubXLog()
        val evaluator = evaluator()

        val result = evaluator.evaluate(
            pluginContext = mockk(relaxed = true),
            intent = FakeIntent("android.intent.action.VIEW"),
            eventId = "evt-ignored",
            source = "test",
        )

        assertNull(result)
    }

    @Test
    fun evaluate_treatsBlacklistBlockWithCodeAsPrefBlock() {
        stubXLog()
        val sms = TestSmsMessage(sender = "1068", body = "otp 123456")
        val evaluator = evaluator(
            sms = sms,
            blacklistResult = BlacklistMatchResult(
                matched = true,
                matchType = "sender",
                pattern = "1068",
                actionDelete = true,
                actionBlock = true,
            ),
            blockSmsEnabled = true,
            parsedCode = "123456",
        )

        val result = requireNotNull(evaluator.evaluate(mockk(relaxed = true), smsIntent(), "evt-1", "test"))

        assertSame(sms, result.smsMsg)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK, result.blockReason)
        assertEquals("pref_block_sms", result.blockReasonWireValue)
        assertEquals("sender", result.blacklistResult.matchType)
        assertEquals("1068", result.blacklistResult.pattern)
        assertFalse(result.blacklistDeleteOnly)
    }

    @Test
    fun evaluate_keepsBlacklistBlockWhenNoCodeExists() {
        stubXLog()
        val sms = TestSmsMessage(sender = "1068", body = "hello")
        val evaluator = evaluator(
            sms = sms,
            blacklistResult = BlacklistMatchResult(
                matched = true,
                matchType = "sender",
                pattern = "1068",
                actionDelete = true,
                actionBlock = true,
            ),
            blockSmsEnabled = true,
            parsedCode = "",
        )

        val result = requireNotNull(evaluator.evaluate(mockk(relaxed = true), smsIntent(), "evt-1b", "test"))

        assertSame(sms, result.smsMsg)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.BLACKLIST, result.blockReason)
        assertEquals("blacklist_block", result.blockReasonWireValue)
        assertFalse(result.blacklistDeleteOnly)
    }

    @Test
    fun evaluate_keepsDeleteOnlyBlacklistWhenPrefBlockDisabled() {
        stubXLog()
        val evaluator = evaluator(
            sms = TestSmsMessage(sender = "1068", body = "otp 123456"),
            blacklistResult = BlacklistMatchResult(
                matched = true,
                actionDelete = true,
                actionBlock = false,
            ),
            blockSmsEnabled = false,
            parsedCode = "123456",
        )

        val result = requireNotNull(evaluator.evaluate(mockk(relaxed = true), smsIntent(), "evt-2", "test"))

        assertNull(result.blockReason)
        assertTrue(result.blacklistDeleteOnly)
    }

    @Test
    fun evaluate_blocksWhenPrefEnabledAndCodeExists() {
        stubXLog()
        val evaluator = evaluator(
            sms = TestSmsMessage(sender = "1068", body = "otp 123456"),
            blockSmsEnabled = true,
            parsedCode = "123456",
        )

        val result = requireNotNull(evaluator.evaluate(mockk(relaxed = true), smsIntent(), "evt-3", "test"))

        assertEquals(SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK, result.blockReason)
        assertEquals("pref_block_sms", result.blockReasonWireValue)
        assertFalse(result.blacklistDeleteOnly)
    }

    @Test
    fun evaluate_allowsWhenBodyDoesNotContainCode() {
        stubXLog()
        val evaluator = evaluator(
            sms = TestSmsMessage(sender = "1068", body = "hello"),
            blockSmsEnabled = true,
            parsedCode = "",
        )

        val result = requireNotNull(evaluator.evaluate(mockk(relaxed = true), smsIntent(), "evt-4", "test"))

        assertNull(result.blockReason)
        assertFalse(result.blacklistDeleteOnly)
    }

    private fun evaluator(
        sms: TestSmsMessage? = null,
        blacklistResult: BlacklistMatchResult = BlacklistMatchResult(matched = false),
        blockSmsEnabled: Boolean = false,
        parsedCode: String? = null,
    ): SmsBlockEvaluator<TestSmsMessage> {
        return SmsBlockEvaluator(
            incomingSmsParser = { sms },
            blacklistMatcher = { _, _, _ -> blacklistResult },
            blockSmsEnabledReader = { blockSmsEnabled },
            smsCodeParser = { _, _ -> parsedCode },
        )
    }

    private fun smsIntent(): Intent {
        return FakeIntent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)
    }

    private fun stubXLog() {
        XLog.setTestSink { _, _ -> }
    }

    private class FakeIntent(
        private val fakeAction: String?,
    ) : Intent() {
        override fun getAction(): String? = fakeAction
    }
}
