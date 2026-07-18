package io.github.magisk317.smscode.runtime.common.store

import java.io.File
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JsonEntityFileStoreTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `store replaces content and leaves no temporary file`() {
        val warnings = mutableListOf<String>()
        val store = testStore(warnings)
        val target = File(tempDir, "entities.json")

        assertTrue(store.storeEntities(target, listOf(Item("first")), Item::class.java))
        assertTrue(store.storeEntities(target, listOf(Item("second")), Item::class.java))

        assertEquals(listOf(Item("second")), store.loadEntities(target, Item::class.java))
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `oversized writes fail without replacing the last good snapshot`() {
        val warnings = mutableListOf<String>()
        val store = testStore(warnings, maxFileBytes = 256)
        val target = File(tempDir, "entities.json")
        assertTrue(store.storeEntities(target, listOf(Item("ok")), Item::class.java))

        assertFalse(store.storeEntities(target, listOf(Item("x".repeat(1000))), Item::class.java))

        assertEquals(listOf(Item("ok")), store.loadEntities(target, Item::class.java))
        assertTrue(warnings.single().contains("exceeds limit"))
    }

    private fun testStore(
        warnings: MutableList<String>,
        maxFileBytes: Long = 1024,
    ): JsonEntityFileStore<String> = JsonEntityFileStore(
        fileResolver = { _, key -> File(tempDir, key) },
        logger = JsonEntityFileStore.Logger { message, _ -> warnings += message },
        maxFileBytes = maxFileBytes,
    )

    @Serializable
    private data class Item(val value: String)
}
