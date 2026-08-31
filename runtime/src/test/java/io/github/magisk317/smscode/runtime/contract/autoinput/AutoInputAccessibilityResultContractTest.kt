package io.github.magisk317.smscode.runtime.contract.autoinput

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoInputAccessibilityResultContractTest {
    @Test
    fun putOrderedResult_writesHandledPayload() {
        val extras = linkedMapOf<String, Any?>()
        AutoInputAccessibilityResultContract.putOrderedResult(
            putBoolean = { key, value -> extras[key] = value },
            putString = { key, value -> extras[key] = value },
            result = AutoInputAccessibilityResultContract.OrderedResult(
                success = false,
                strategy = "focused_editable",
                reason = "no_focus",
                windowPackage = "com.example.target",
            ),
        )

        assertEquals(true, extras[AutoInputAccessibilityResultContract.EXTRA_HANDLED])
        assertEquals(false, extras[AutoInputAccessibilityResultContract.EXTRA_SUCCESS])
        assertEquals("focused_editable", extras[AutoInputAccessibilityResultContract.EXTRA_STRATEGY])
        assertEquals("no_focus", extras[AutoInputAccessibilityResultContract.EXTRA_REASON])
        assertEquals(
            "com.example.target",
            extras[AutoInputAccessibilityResultContract.EXTRA_WINDOW_PACKAGE],
        )
    }

    @Test
    fun readOrderedResult_returnsNullWhenNotHandled() {
        assertNull(
            AutoInputAccessibilityResultContract.readOrderedResult(
                getBoolean = { _, defaultValue -> defaultValue },
                getString = { null },
            ),
        )
    }

    @Test
    fun readOrderedResult_acceptsPayload() {
        val extras = mapOf(
            AutoInputAccessibilityResultContract.EXTRA_HANDLED to true,
            AutoInputAccessibilityResultContract.EXTRA_SUCCESS to true,
            AutoInputAccessibilityResultContract.EXTRA_STRATEGY to "group",
            AutoInputAccessibilityResultContract.EXTRA_REASON to "filled",
            AutoInputAccessibilityResultContract.EXTRA_WINDOW_PACKAGE to "com.example.target",
        )

        assertEquals(
            AutoInputAccessibilityResultContract.OrderedResult(
                success = true,
                strategy = "group",
                reason = "filled",
                windowPackage = "com.example.target",
            ),
            AutoInputAccessibilityResultContract.readOrderedResult(
                getBoolean = { key, defaultValue -> extras[key] as? Boolean ?: defaultValue },
                getString = { key -> extras[key] as? String },
            ),
        )
    }
}
