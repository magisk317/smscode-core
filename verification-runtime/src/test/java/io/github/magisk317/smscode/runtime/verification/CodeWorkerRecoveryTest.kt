package io.github.magisk317.smscode.runtime.verification

import android.content.Context
import android.content.Intent
import android.os.Handler
import io.github.magisk317.smscode.verification.SmsProcessingClaims
import io.github.magisk317.xposed.logging.XLog
import io.mockk.mockk
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodeWorkerRecoveryTest {
    private val claims = SmsProcessingClaims()
    private val liveClaims = SmsProcessingClaims()
    private var lastPlan: SmsCodePostParseCoordinator.ParsedSmsPlan? = null
    private val message = TestSmsMessage(sender = "test", body = "test message", date = 123L, smsCode = "123456")
    private var dispatches = 0
    private var reject = false

    @BeforeEach
    fun setUp() { XLog.setTestSink { _, _ -> } }

    @AfterEach
    fun tearDown() { XLog.setTestSink(null) }

    @Test
    fun `separate live workers share completed processing across provider state changes`() {
        assertNotNull(worker().parse())
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
    }

    @Test
    fun `rejected scheduling releases claim so the retained event can retry`() {
        reject = true
        assertNull(worker().parse())
        reject = false
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
    }

    @Test
    fun `pending observer work does not prevent live copy and notification`() {
        val lease = (claims.claim("test", "test message", 123L) as SmsProcessingClaims.ClaimResult.Acquired).lease
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
        assertNotNull(lastPlan?.notificationPlan)
        assertEquals(false, lastPlan?.shouldRecord)
        assertNull(lastPlan?.autoInputDelayMs)
        claims.complete(lease)
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
    }

    @Test
    fun `observer completion preserves live notification while avoiding duplicate shared actions`() {
        val lease = (claims.claim("test", "test message", 123L) as SmsProcessingClaims.ClaimResult.Acquired).lease
        claims.complete(lease)
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
        assertNotNull(lastPlan?.notificationPlan)
        assertEquals(false, lastPlan?.shouldRecord)
        assertNull(lastPlan?.autoInputDelayMs)
        assertNotNull(worker().parse())
        assertEquals(1, dispatches)
    }

    @Test
    fun `same body received at a different time remains a new message`() {
        assertNotNull(worker().parse())
        assertNotNull(worker(message.copy(date = 124L)).parse())
        assertEquals(2, dispatches)
    }

    private fun worker(sms: TestSmsMessage = message): CodeWorker<TestSmsMessage, TestParseResult> = CodeWorker(
        pluginContext = mockk<Context>(),
        phoneContext = mockk<Context>(),
        smsIntent = mockk<Intent>(),
        settingsLoader = { settings() },
        moduleEnabledReader = { true },
        verboseLogReader = { false },
        logLevelSetter = {},
        currentLogLevelReader = { 0 },
        defaultLogLevel = 0,
        parseRunner = { _, _, _, _, _ -> CodeWorker.ParseOutcome(smsMsg = sms) },
        parsedSmsDispatcher = { _, _, _, _, _, _, plan ->
            lastPlan = plan
            if (reject) throw RejectedExecutionException("test rejection")
            dispatches++
        },
        parseResultFactory = ::TestParseResult,
        uiHandler = mockk<Handler>(),
        executorFactory = { Executors.newSingleThreadScheduledExecutor() },
        processingClaims = claims,
        liveClaims = liveClaims,
    )

    private fun settings() = SmsCodePostParseCoordinator.Settings(
        showNotification = true,
        autoCancelNotification = true,
        notificationRetentionMs = 1_000L,
        autoInputEnabled = false,
        autoInputDelayMs = 0L,
        inputIntervalMs = 0L,
        copyToClipboardEnabled = true,
        showToast = false,
        recordSmsEnabled = true,
        blockSmsEnabled = true,
        markAsReadEnabled = false,
        deleteSmsEnabled = false,
        deduplicateSmsEnabled = true,
    )
}
