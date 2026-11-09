package io.github.magisk317.smscode.xposed.hook.telephony

import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderBlacklistEngine.BLOCK_REASON
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision.Block
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision.Allow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsProviderBlacklistEngineTest {
    private fun matched(
        pattern: String = "拒收",
        actionDelete: Boolean = false,
        actionBlock: Boolean = false,
    ) = BlacklistMatchResult(
        matched = true,
        matchType = "regex",
        pattern = pattern,
        actionDelete = actionDelete,
        actionBlock = actionBlock,
    )

    private fun unmatched() = BlacklistMatchResult(matched = false)

    @Test
    fun `disabled module allows every write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = false,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(matched(actionBlock = true)),
        )
        assertEquals(Allow, decision.write)
        assertNull(decision.matched)
    }

    @Test
    fun `missing mobile entitlement allows every write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = false,
            conflictSuppressed = false,
            matches = listOf(matched(actionBlock = true)),
        )
        assertEquals(Allow, decision.write)
        assertNull(decision.matched)
    }

    @Test
    fun `relay conflict arbitration allows every write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = true,
            matches = listOf(matched(actionBlock = true)),
        )
        assertEquals(Allow, decision.write)
        assertNull(decision.matched)
    }

    @Test
    fun `block entry cancels the provider write with attribution`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(matched(actionBlock = true, pattern = "拒收请回复R")),
        )
        assertTrue(decision.write is Block)
        assertEquals(BLOCK_REASON, (decision.write as Block).reason)
        assertEquals("拒收请回复R", decision.matched?.pattern)
    }

    @Test
    fun `delete-only entry cancels the provider write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(matched(actionDelete = true)),
        )
        assertTrue(decision.write is Block)
    }

    @Test
    fun `matched entry without delete or block actions keeps the write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(matched()),
        )
        assertEquals(Allow, decision.write)
        assertNull(decision.matched)
    }

    @Test
    fun `unmatched drafts keep the write`() {
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(unmatched(), unmatched()),
        )
        assertEquals(Allow, decision.write)
        assertNull(decision.matched)
    }

    @Test
    fun `first hitting draft wins the attribution`() {
        val first = matched(pattern = "first", actionDelete = true)
        val second = matched(pattern = "second", actionBlock = true)
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = true,
            conflictSuppressed = false,
            matches = listOf(unmatched(), first, second),
        )
        assertTrue(decision.write is Block)
        assertEquals("first", decision.matched?.pattern)
    }
}
