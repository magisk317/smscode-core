package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.smscode.xposed.utils.XLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatchIntentProcessorTest {

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun handle_passesParsedSmsIntoBlacklistAndDecisionPipeline() {
        stubXLog()
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val intent = Intent()
        var matchedSender: String? = null
        var matchedBody: String? = null
        val parseResult = TestParseResult(isBlockSms = true)
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = {
                TestSmsMessage(
                    sender = "1068",
                    body = "otp 123456",
                    date = 100L,
                )
            },
            blacklistMatcher = { _, sender, body ->
                matchedSender = sender
                matchedBody = body
                BlacklistMatchResult(
                    matched = true,
                    matchType = "number",
                    pattern = "1068",
                    actionDelete = true,
                    actionBlock = false,
                )
            },
            codeParser = { _, _, _, _ -> parseResult },
        )

        val outcome = processor.handle(intent, "evt-1")

        assertEquals("1068", matchedSender)
        assertEquals("otp 123456", matchedBody)
        assertEquals("1068", outcome.smsMsg?.sender)
        assertTrue(outcome.blacklistResult.matched)
        assertEquals(parseResult, outcome.parseResult)
        assertTrue(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
    }

    @Test
    fun handle_reportsNullParseResultWhenCodeWorkerMisses() {
        stubXLog()
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val intent = Intent()
        val processor = SmsDispatchIntentProcessor<TestSmsMessage>(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { null },
            blacklistMatcher = { _, _, _ -> BlacklistMatchResult(matched = false) },
            codeParser = { _, _, _, _ -> null },
        )

        val outcome = processor.handle(intent, "evt-2")

        assertNull(outcome.smsMsg)
        assertFalse(outcome.blacklistResult.matched)
        assertNull(outcome.parseResult)
        assertFalse(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
        assertFalse(outcome.decision.shouldAllowSystemPersist)
    }

    private fun stubXLog() {
        XLog.setTestSink { _, _ -> }
    }
}
