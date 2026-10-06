package io.github.magisk317.smscode.runtime.common.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeNotificationOwnerTest {

    @Test
    fun `normalize defaults unset values to phone owned`() {
        assertEquals(CodeNotificationOwner.PHONE, CodeNotificationOwner.normalize(null))
        assertEquals(CodeNotificationOwner.PHONE, CodeNotificationOwner.normalize(""))
        assertEquals(CodeNotificationOwner.PHONE, CodeNotificationOwner.normalize("unknown"))
    }

    @Test
    fun `normalize preserves explicit app owned selection`() {
        assertEquals(CodeNotificationOwner.APP, CodeNotificationOwner.normalize(CodeNotificationOwner.APP))
    }
}
