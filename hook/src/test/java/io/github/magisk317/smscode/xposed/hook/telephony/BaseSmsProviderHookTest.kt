package io.github.magisk317.smscode.xposed.hook.telephony

import org.junit.jupiter.api.Assertions.assertFalse
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
}
