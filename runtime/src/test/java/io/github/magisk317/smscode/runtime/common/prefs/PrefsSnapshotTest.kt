package io.github.magisk317.smscode.runtime.common.prefs

import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefsSnapshotTest {

    private val store = mutableMapOf<String, PrefsSnapshot.Entry>()
    private var clock = 1_000_000L
    private var writes = 0

    private fun snapshot(maxAgeMs: Long = PrefsSnapshot.DEFAULT_MAX_AGE_MS) = PrefsSnapshot(
        read = { store[it] },
        write = { key, entry -> writes++; store[key] = entry },
        clock = { clock },
        maxAgeMs = maxAgeMs,
    )

    @Test
    fun readsBackABooleanItRecorded() {
        val snapshot = snapshot()
        snapshot.recordBoolean("show", false)

        val read = snapshot.readBoolean("show")

        assertEquals(PrefRead.Hit(false, PrefsSnapshot.SOURCE_SNAPSHOT), read)
    }

    @Test
    fun reportsUnavailableWhenNothingWasRecorded() {
        assertEquals(PrefRead.Unavailable, snapshot().readBoolean("show"))
    }

    @Test
    fun agesOutAnEntryThatWasNeverRefreshed() {
        val snapshot = snapshot(maxAgeMs = 1_000L)
        snapshot.recordBoolean("show", false)

        clock += 1_001L

        assertEquals(PrefRead.Unavailable, snapshot.readBoolean("show"))
    }

    @Test
    fun keepsServingAnEntryInsideTheAgeLimit() {
        val snapshot = snapshot(maxAgeMs = 1_000L)
        snapshot.recordBoolean("show", false)

        clock += 999L

        assertEquals(PrefRead.Hit(false, PrefsSnapshot.SOURCE_SNAPSHOT), snapshot.readBoolean("show"))
    }

    @Test
    fun refreshExtendsTheLifeOfAnEntry() {
        val snapshot = snapshot(maxAgeMs = 1_000L)
        snapshot.recordBoolean("show", false)
        clock += 900L
        snapshot.recordBoolean("show", true)
        clock += 900L

        assertEquals(PrefRead.Hit(true, PrefsSnapshot.SOURCE_SNAPSHOT), snapshot.readBoolean("show"))
    }

    @Test
    fun rejectsAValueStoredUnderADifferentType() {
        val snapshot = snapshot()
        snapshot.recordString("show", "false")

        assertEquals(PrefRead.Unavailable, snapshot.readBoolean("show"))
    }

    @Test
    fun skipsTheWriteWhenTheValueDidNotChange() {
        val snapshot = snapshot()
        snapshot.recordBoolean("show", false)
        val afterFirst = writes

        snapshot.recordBoolean("show", false)

        assertEquals(afterFirst, writes, "an unchanged value should not hit the backing store again")
    }

    @Test
    fun writesAgainWhenTheValueChanges() {
        val snapshot = snapshot()
        snapshot.recordBoolean("show", false)
        val afterFirst = writes

        snapshot.recordBoolean("show", true)

        assertTrue(writes > afterFirst, "a changed value must reach the backing store")
    }

    @Test
    fun roundTripsStringAndIntValues() {
        val snapshot = snapshot()
        snapshot.recordString("owner", "phone")
        snapshot.recordInt("retention", 42)

        assertEquals(PrefRead.Hit("phone", PrefsSnapshot.SOURCE_SNAPSHOT), snapshot.readString("owner"))
        assertEquals(PrefRead.Hit(42, PrefsSnapshot.SOURCE_SNAPSHOT), snapshot.readInt("retention"))
    }

    @Test
    fun survivesTheBackingStoreThrowing() {
        val snapshot = PrefsSnapshot(
            read = { throw IllegalStateException("disk gone") },
            write = { _, _ -> throw IllegalStateException("disk gone") },
            clock = { clock },
        )

        assertEquals(PrefRead.Unavailable, snapshot.readBoolean("show"))
        snapshot.recordBoolean("show", true)
    }
}
