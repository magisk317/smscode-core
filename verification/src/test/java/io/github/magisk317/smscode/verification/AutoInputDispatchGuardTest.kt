package io.github.magisk317.smscode.verification

import android.content.Context
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoInputDispatchGuardTest {
    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun claim_skipsSecondMessageWhenCodeMatchesWithinWindow() {
        XLog.setTestSink { _, _ -> }
        val pluginContext = mockk<Context>(relaxed = true)
        val claimedAt = LinkedHashMap<String, Long>()
        var now = 5_000L
        val gateClaimer =
            { _: Context, _: String, keys: List<String>, windowMs: Long, _: Int ->
                val blockedKey = keys.firstOrNull { key ->
                    val claimedTime = claimedAt[key]
                    claimedTime != null && now - claimedTime <= windowMs
                }
                if (blockedKey != null) {
                    AutoInputDispatchGuard.ClaimResult(
                        claimed = false,
                        ageMs = now - (claimedAt[blockedKey] ?: now),
                        key = blockedKey,
                    )
                } else {
                    keys.forEach { key -> claimedAt[key] = now }
                    AutoInputDispatchGuard.ClaimResult(claimed = true)
                }
            }

        val firstClaimed = AutoInputDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = TestSmsMessage(
                sender = "10690665401526040",
                body = "JDCloud code 063664",
                company = "JDCloud",
                smsCode = "063664",
            ),
            delayMs = 0L,
            gateClaimer = gateClaimer,
        )

        now += 1_000L

        val secondClaimed = AutoInputDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = TestSmsMessage(
                sender = "JDCloud",
                body = "code 063664",
                company = "JDCloud",
                smsCode = "063664",
            ),
            delayMs = 0L,
            gateClaimer = gateClaimer,
        )

        assertTrue(firstClaimed)
        assertFalse(secondClaimed)
    }

    @Test
    fun claim_allowsBlankMessagesWithoutTouchingGate() {
        val pluginContext = mockk<Context>(relaxed = true)
        var gateCalled = false

        val claimed = AutoInputDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = TestSmsMessage(),
            delayMs = 0L,
        ) { _, _, _, _, _ ->
            gateCalled = true
            AutoInputDispatchGuard.ClaimResult(claimed = false)
        }

        assertTrue(claimed)
        assertFalse(gateCalled)
    }
}
