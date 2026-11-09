package io.github.magisk317.smscode.xposed.hook.telephony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseSmsProviderHookTest {

    @Test
    fun isTargetPackage_matchesTelephonyProvider() {
        assertTrue(
            BaseSmsProviderHook.isTargetPackage(SmsProviderHookInstaller.TARGET_PACKAGE),
        )
    }

    @Test
    fun isTargetPackage_rejectsOtherProcesses() {
        // Both hosts load this hook into every process; only the telephony provider may
        // install it, otherwise the same SMS write would be observed twice.
        assertFalse(BaseSmsProviderHook.isTargetPackage("com.android.mms"))
        assertFalse(BaseSmsProviderHook.isTargetPackage("com.android.providers.contacts"))
        assertFalse(BaseSmsProviderHook.isTargetPackage(""))
        assertFalse(BaseSmsProviderHook.isTargetPackage(null))
    }

    @Test
    fun isTargetPackage_isCaseSensitive() {
        assertFalse(
            BaseSmsProviderHook.isTargetPackage(
                SmsProviderHookInstaller.TARGET_PACKAGE.uppercase(),
            ),
        )
    }

    @Test
    fun isInboundType_treatsAbsentAsInbound() {
        // Xiaomi network SMS rows may omit the type column.
        assertTrue(SmsProviderHookInstaller.isInboundType(null))
    }

    @Test
    fun isInboundType_acceptsInboxOnly() {
        assertTrue(SmsProviderHookInstaller.isInboundType(1))
        assertTrue(SmsProviderHookInstaller.isInboundType(1L))
    }

    @Test
    fun isInboundType_rejectsNonInboxTypes() {
        // 2 sent, 3 draft, 4 outbox, 5 failed, 6 queued.
        assertFalse(SmsProviderHookInstaller.isInboundType(2))
        assertFalse(SmsProviderHookInstaller.isInboundType(3))
        assertFalse(SmsProviderHookInstaller.isInboundType(4))
        assertFalse(SmsProviderHookInstaller.isInboundType(5))
        assertFalse(SmsProviderHookInstaller.isInboundType(6))
    }

    @Test
    fun isInboundType_rejectsNonNumericType() {
        assertFalse(SmsProviderHookInstaller.isInboundType("1"))
    }

    @Test
    fun extractInboundDrafts_mapsInboundRow() {
        val drafts = SmsProviderHookInstaller.extractInboundDrafts(
            listOf(
                mapOf(
                    SmsProviderHookInstaller.KEY_ADDRESS to "+8613800138000",
                    SmsProviderHookInstaller.KEY_BODY to "拒收请回复R",
                    SmsProviderHookInstaller.KEY_DATE to 1730879680000L,
                    SmsProviderHookInstaller.KEY_TYPE to 1,
                ),
            ),
        )
        assertEquals(1, drafts.size)
        assertEquals("+8613800138000", drafts[0].address)
        assertEquals("拒收请回复R", drafts[0].body)
        assertEquals(1730879680000L, drafts[0].date)
        assertEquals(1, drafts[0].type)
    }

    @Test
    fun extractInboundDrafts_keepsTypeLessRow() {
        val drafts = SmsProviderHookInstaller.extractInboundDrafts(
            listOf(mapOf(SmsProviderHookInstaller.KEY_ADDRESS to "10690", SmsProviderHookInstaller.KEY_BODY to "hi")),
        )
        assertEquals(1, drafts.size)
        assertNull(drafts[0].type)
    }

    @Test
    fun extractInboundDrafts_filtersNonInboundRows() {
        val drafts = SmsProviderHookInstaller.extractInboundDrafts(
            listOf(
                mapOf(SmsProviderHookInstaller.KEY_TYPE to 2, SmsProviderHookInstaller.KEY_BODY to "sent"),
                mapOf(SmsProviderHookInstaller.KEY_TYPE to 4, SmsProviderHookInstaller.KEY_BODY to "outbox"),
                mapOf(SmsProviderHookInstaller.KEY_BODY to "typeless"),
            ),
        )
        assertEquals(1, drafts.size)
        assertEquals("typeless", drafts[0].body)
    }

    @Test
    fun extractInboundDrafts_handlesEmptyRows() {
        assertTrue(SmsProviderHookInstaller.extractInboundDrafts(emptyList()).isEmpty())
    }

    @Test
    fun blockingResultFor_insertCancelsWithNull() {
        assertNull(SmsProviderHookInstaller.blockingResultFor("insert"))
    }

    @Test
    fun blockingResultFor_bulkInsertCancelsWithZero() {
        assertEquals(0, SmsProviderHookInstaller.blockingResultFor("bulkInsert"))
    }
}
