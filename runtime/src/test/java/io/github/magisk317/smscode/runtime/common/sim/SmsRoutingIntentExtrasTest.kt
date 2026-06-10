package io.github.magisk317.smscode.runtime.common.sim

import io.github.magisk317.smscode.runtime.contract.sim.SimSlotLabelFormatter
import io.github.magisk317.smscode.runtime.contract.sim.SmsRoutingMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmsRoutingIntentExtrasTest {
    @Test
    fun fromRawValues_keepsZeroBasedSlotAndSubscription() {
        val metadata = SmsRoutingIntentExtras.fromRawValues(
            simSlot = 1,
            subId = 8,
        )

        assertEquals(1, metadata.simSlot)
        assertEquals(8, metadata.subId)
    }

    @Test
    fun fromRawValues_normalizesOneBasedRawSlotTwo() {
        val metadata = SmsRoutingIntentExtras.fromRawValues(
            simSlot = 2,
            subId = null,
        )

        assertEquals(1, metadata.simSlot)
        assertNull(metadata.subId)
    }

    @Test
    fun fromRawValues_resolvesSlotFromSubscription() {
        val metadata = SmsRoutingIntentExtras.fromRawValues(
            simSlot = null,
            subId = 1,
            slotIndexResolver = { subId -> if (subId == 1) 0 else -1 },
        )

        assertEquals(0, metadata.simSlot)
        assertEquals(1, metadata.subId)
    }

    @Test
    fun fromRawValues_prefersResolvedSubscriptionOverRawSlot() {
        val metadata = SmsRoutingIntentExtras.fromRawValues(
            simSlot = 1,
            subId = 1,
            slotIndexResolver = { subId -> if (subId == 1) 0 else -1 },
        )

        assertEquals(0, metadata.simSlot)
        assertEquals(1, metadata.subId)
    }

    @Test
    fun normalized_prefersSlotResolvedFromSubId() {
        val metadata = SmsRoutingMetadata(simSlot = 0, subId = 8)
            .normalized { subId -> if (subId == 8) 1 else -1 }

        assertEquals(1, metadata.simSlot)
        assertEquals(8, metadata.subId)
    }

    @Test
    fun normalized_mapsOneBasedRawSlotTwoToSlotOne() {
        val metadata = SmsRoutingMetadata(simSlot = 2, subId = null).normalized()

        assertEquals(1, metadata.simSlot)
        assertNull(metadata.subId)
    }

    @Test
    fun formatterUsesRemarkWhenPresent() {
        val label = SimSlotLabelFormatter.format(0) { "Main" }

        assertEquals("Main", label)
    }

    @Test
    fun formatterFallsBackToSimNameWithoutRemark() {
        val label = SimSlotLabelFormatter.format(1) { "" }

        assertEquals("SIM2", label)
    }
}
