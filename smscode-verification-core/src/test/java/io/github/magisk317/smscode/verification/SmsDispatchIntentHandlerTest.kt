package io.github.magisk317.smscode.verification

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatchIntentHandlerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun handle_stopsWhenRuntimeUnavailable() {
        stubXLog()
        val handler = SmsDispatchIntentHandler<TestSmsMessage>(
            runtimeResolver = { null },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            dispatchProcessor = { _, _, _, _ -> error("unused") },
            conflictNotifier = { _, _, _, _ -> },
        )

        val outcome = handler.handle(
            intent = mockk(relaxed = true),
            eventId = "evt-1",
            inboundSmsHandler = Any(),
            receiver = Any(),
        )

        assertEquals(SmsDispatchIntentHandler.StopReason.RUNTIME_UNAVAILABLE, outcome.stopReason)
        assertFalse(outcome.inboundBlocked)
    }

    @Test
    fun handle_notifiesWhenConflictSuppressed() {
        stubXLog()
        val runtime = runtime()
        var notifiedEventId: String? = null
        var notifiedSource: String? = null
        var suppressionStage: String? = null
        val handler = SmsDispatchIntentHandler<TestSmsMessage>(
            runtimeResolver = { runtime },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> true },
            dispatchProcessor = { _, _, _, _ -> error("unused") },
            conflictNotifier = { _, _, eventId, source ->
                notifiedEventId = eventId
                notifiedSource = source
            },
            suppressionLogger = { stage -> suppressionStage = stage },
        )

        val outcome = handler.handle(
            intent = mockk(relaxed = true),
            eventId = "evt-2",
            inboundSmsHandler = Any(),
            receiver = Any(),
        )

        assertEquals(SmsDispatchIntentHandler.StopReason.CONFLICT_SUPPRESSED, outcome.stopReason)
        assertEquals("evt-2", notifiedEventId)
        assertEquals("SmsHandlerHook#dispatchIntent", notifiedSource)
        assertEquals("dispatchIntent", suppressionStage)
    }

    @Test
    fun handle_blocksInboundWhenDecisionRequestsSmsBlock() {
        stubXLog()
        val runtime = runtime()
        var blockedReason: String? = null
        var blockedEventId: String? = null
        val handler = SmsDispatchIntentHandler<TestSmsMessage>(
            runtimeResolver = { runtime },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            dispatchProcessor = { _, _, _, _ ->
                SmsDispatchIntentProcessor.Outcome(
                    smsMsg = null,
                    blacklistResult = BlacklistMatchResult(matched = false),
                    parseResult = TestParseResult(isBlockSms = true),
                    decision = SmsHandlerDispatchDecision.Decision(
                        shouldDeleteByBlacklist = false,
                        blockReason = SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK,
                    ),
                )
            },
            conflictNotifier = { _, _, _, _ -> },
            inboundBlocker = { _, _, reason, eventId ->
                blockedReason = reason
                blockedEventId = eventId
            },
        )

        val outcome = handler.handle(
            intent = mockk(relaxed = true),
            eventId = "evt-3",
            inboundSmsHandler = Any(),
            receiver = Any(),
        )

        assertEquals(SmsDispatchIntentHandler.StopReason.SMS_BLOCKED, outcome.stopReason)
        assertTrue(outcome.inboundBlocked)
        assertEquals("pref_block_sms", blockedReason)
        assertEquals("evt-3", blockedEventId)
    }

    @Test
    fun handle_schedulesBlacklistDeleteWithoutStoppingHealthyFlow() {
        stubXLog()
        val runtime = runtime()
        var deletedSender: String? = null
        val smsMsg = TestSmsMessage(
            sender = "1068",
            body = "otp 123456",
            date = 100L,
        )
        val handler = SmsDispatchIntentHandler(
            runtimeResolver = { runtime },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            dispatchProcessor = { _, _, _, _ ->
                SmsDispatchIntentProcessor.Outcome(
                    smsMsg = smsMsg,
                    blacklistResult = BlacklistMatchResult(matched = false),
                    parseResult = null,
                    decision = SmsHandlerDispatchDecision.Decision(
                        shouldDeleteByBlacklist = true,
                    ),
                )
            },
            conflictNotifier = { _, _, _, _ -> },
            blacklistDeleteScheduler = { _, _, msg ->
                deletedSender = msg.sender
            },
        )

        val outcome = handler.handle(
            intent = mockk(relaxed = true),
            eventId = "evt-4",
            inboundSmsHandler = Any(),
            receiver = Any(),
        )

        assertNull(outcome.stopReason)
        assertFalse(outcome.inboundBlocked)
        assertEquals("1068", deletedSender)
    }

    private fun runtime(): VerificationRuntimeContext {
        return object : VerificationRuntimeContext {
            override val pluginContext: Context = mockk(relaxed = true)
            override val phoneContext: Context = mockk(relaxed = true)
        }
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.w(any(), *anyVararg()) } returns Unit
        every { XLog.i(any(), *anyVararg()) } returns Unit
        every { XLog.e(any(), *anyVararg()) } returns Unit
    }
}
