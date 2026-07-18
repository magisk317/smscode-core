package io.github.magisk317.smscode.runtime.common.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferenceSpecTest {
    private val enabled = PreferenceSpec.boolean("enabled", defaultValue = true)
    private val label = PreferenceSpec.string("label", defaultValue = "default")
    private val retries = PreferenceSpec.int("retries", defaultValue = 3)
    private val ratio = PreferenceSpec.float("ratio", defaultValue = 1.5f)

    @Test
    fun `snapshot reads supported primitive types through specs`() {
        val snapshot = PreferenceSnapshot.from(
            mapOf(
                "enabled" to false,
                "label" to "stored",
                "retries" to 7,
                "ratio" to 2.5f,
            ),
        )

        assertEquals(false, snapshot[enabled])
        assertEquals("stored", snapshot[label])
        assertEquals(7, snapshot[retries])
        assertEquals(2.5f, snapshot[ratio])
        assertTrue(snapshot.read(enabled).isStored)
    }

    @Test
    fun `snapshot distinguishes missing and mismatched defaults`() {
        val snapshot = PreferenceSnapshot.from(
            mapOf(
                "enabled" to "true",
                "retries" to 4L,
            ),
        )

        assertEquals(
            PreferenceValue(true, PreferenceValueOrigin.DEFAULT_TYPE_MISMATCH),
            snapshot.read(enabled),
        )
        assertEquals(
            PreferenceValue(3, PreferenceValueOrigin.DEFAULT_TYPE_MISMATCH),
            snapshot.read(retries),
        )
        assertEquals(
            PreferenceValue("default", PreferenceValueOrigin.DEFAULT_MISSING),
            snapshot.read(label),
        )
        assertFalse(snapshot.read(label).isStored)
    }

    @Test
    fun `snapshot copies the source map`() {
        val source = mutableMapOf<String, Any>("enabled" to false)
        val snapshot = PreferenceSnapshot.from(source)

        source["enabled"] = true

        assertFalse(snapshot[enabled])
    }

    @Test
    fun `spec rejects blank keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceSpec.boolean("  ", defaultValue = false)
        }
    }

    @Test
    fun `change set is typed and last write wins per key`() {
        val changes = preferenceChangeSet {
            set(enabled, true)
            set(label, "stored")
            set(enabled, false)
            remove(retries)
        }

        assertEquals(
            listOf(
                PreferenceChange.PutBoolean("enabled", false),
                PreferenceChange.PutString("label", "stored"),
                PreferenceChange.Remove("retries"),
            ),
            changes.changes,
        )
        assertEquals(setOf("enabled", "label", "retries"), changes.changedKeys)
    }
}
