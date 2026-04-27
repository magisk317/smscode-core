package io.github.magisk317.smscode.verification

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoInputActionHelperTest {

    @Test
    fun run_skipsSecondDispatchWhenCodeMatchesWithinWindow() {
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val sentCodes = mutableListOf<String>()
        val claimedAt = LinkedHashMap<String, Long>()
        var now = 1_000L

        val sharedGateClaimer =
            { _: Context, _: String, keys: List<String>, windowMs: Long, _: Int ->
                val blockedKey = keys.firstOrNull { key ->
                    val claimedTime = claimedAt[key]
                    claimedTime != null && now - claimedTime <= windowMs
                }
                if (blockedKey != null) {
                    AutoInputActionHelper.ClaimResult(
                        claimed = false,
                        ageMs = now - (claimedAt[blockedKey] ?: now),
                        key = blockedKey,
                    )
                } else {
                    keys.forEach { key -> claimedAt[key] = now }
                    AutoInputActionHelper.ClaimResult(claimed = true)
                }
            }

        helper(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = TestSmsMessage(
                sender = "10690665401526040",
                body = "京东云验证码 063664",
                smsCode = "063664",
                company = "京东云",
            ),
            sharedGateClaimer = sharedGateClaimer,
            currentTimeMillis = { now },
            inputSender = { _, code, _, _, _ ->
                if (code != null) {
                    sentCodes += code
                }
            },
        ).run()

        now += 1_000L

        helper(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = TestSmsMessage(
                sender = "JDCloud",
                body = "验证码 063664，请勿泄露",
                smsCode = "063664",
                company = "京东云",
            ),
            sharedGateClaimer = sharedGateClaimer,
            currentTimeMillis = { now },
            inputSender = { _, code, _, _, _ ->
                if (code != null) {
                    sentCodes += code
                }
            },
        ).run()

        assertEquals(listOf("063664"), sentCodes)
    }

    private fun helper(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: TestSmsMessage,
        sharedGateClaimer: (Context, String, List<String>, Long, Int) -> AutoInputActionHelper.ClaimResult,
        currentTimeMillis: () -> Long,
        inputSender: (Context, String?, Boolean, Long, Long?) -> Unit,
    ): AutoInputActionHelper<TestSmsMessage> {
        return AutoInputActionHelper(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            deduplicateEnabled = true,
            dispatchDelayMs = 0L,
            deduplicateReader = { true },
            sharedGateClaimer = sharedGateClaimer,
            packageBlockedChecker = { false },
            autoEnterReader = { false },
            inputIntervalReader = { 0L },
            inputSender = inputSender,
            currentTimeMillis = currentTimeMillis,
            runningProcessesProvider = { null },
            runningTasksProvider = { null },
        )
    }
}
