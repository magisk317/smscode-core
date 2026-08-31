package io.github.magisk317.smscode.runtime.common.utils

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SharedRuntimeGateTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `file-backed gate rejects a repeated key within the window`() {
        val file = File(tempDir, "gate")

        assertTrue(SharedRuntimeGate.claimWithinWindow(file, "same", 60_000L).claimed)
        val repeated = SharedRuntimeGate.claimWithinWindow(file, "same", 60_000L)

        assertFalse(repeated.claimed)
        assertEquals("same", repeated.key)
    }

    @Test
    fun `concurrent claims in one provider process have a single winner`() {
        val file = File(tempDir, "gate")
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(
                List(8) {
                    Callable { SharedRuntimeGate.claimWithinWindow(file, "same", 60_000L).claimed }
                },
            ).map { it.get() }

            assertEquals(1, results.count { it })
        } finally {
            executor.shutdownNow()
        }
    }
}
