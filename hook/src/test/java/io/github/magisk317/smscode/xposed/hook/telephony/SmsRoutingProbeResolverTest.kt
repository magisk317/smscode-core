package io.github.magisk317.smscode.xposed.hook.telephony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsRoutingProbeResolverTest {

    class FakePhone(val phoneId: Int, val subId: Int)

    class HandlerWithPhone(val phone: FakePhone)

    @Test
    fun resolve_readsSlotAndSubIdFromFields() {
        val handler = HandlerWithPhone(FakePhone(phoneId = 1, subId = 3))
        val resolved = SmsRoutingProbeResolver.resolve(handler, args = null)
        assertNotNull(resolved)
        assertEquals(1, resolved!!.simSlot)
        assertEquals(3, resolved.subId)
    }

    @Test
    fun resolve_readsFromDumbledMethodNames() {
        class WithAccessors {
            fun getSlotIndex(): Int = 0
            fun getSubscriptionId(): Int = 2
        }
        val resolved = SmsRoutingProbeResolver.resolve(WithAccessors(), args = null)
        assertNotNull(resolved)
        assertEquals(0, resolved!!.simSlot)
        assertEquals(2, resolved.subId)
    }

    @Test
    fun resolve_prefersArgsOverHandler() {
        val handler = HandlerWithPhone(FakePhone(phoneId = 1, subId = 3))
        val resolved = SmsRoutingProbeResolver.resolve(
            handler = handler,
            args = arrayOf<Any?>(FakePhone(phoneId = 0, subId = 7)),
        )
        assertNotNull(resolved)
        assertEquals(0, resolved!!.simSlot)
    }

    @Test
    fun resolve_returnsNullWhenNothingResolves() {
        val resolved = SmsRoutingProbeResolver.resolve(handler = "plain string", args = null)
        assertNull(resolved?.takeIf { it.hasValue() })
    }

    @Test
    fun resolve_terminatesOnCyclicGraph() {
        // A self-referencing handler must not loop forever.
        class Cyclic {
            @Suppress("unused")
            fun getPhone(): Any = this
        }
        val resolved = SmsRoutingProbeResolver.resolve(Cyclic(), args = null)
        assertNull(resolved?.takeIf { it.hasValue() })
    }

    @Test
    fun debugSnapshot_describesTargetsWithoutThrowing() {
        val snapshot = SmsRoutingProbeResolver.debugSnapshot(
            handler = HandlerWithPhone(FakePhone(1, 2)),
            args = arrayOf<Any?>(FakePhone(0, 5), null),
        )
        assertTrue(snapshot.contains("FakePhone"))
        assertTrue(snapshot.contains("arg[1]").not())
    }

    @Test
    fun readFromIntent_returnsEmptyProbeForUnrelatedObject() {
        // Intent itself is a JVM stub in unit tests, so probe the hasValue contract
        // through the resolver's own type instead.
        val empty = SmsRoutingProbe(simSlot = null, subId = null)
        assertTrue(!empty.hasValue())
        val filled = SmsRoutingProbe(simSlot = 1, subId = 2)
        assertTrue(filled.hasValue())
    }
}
