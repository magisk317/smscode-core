package io.github.magisk317.smscode.runtime.common.utils

import io.github.magisk317.xposed.logging.MagiskLogPolicy
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SharedRuntimeGateTest {
    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun installDebugPolicy() {
        MagiskLogPolicy.install(isDiagnosticBuild = true)
    }

    @AfterEach
    fun restoreReleaseSafePolicy() {
        MagiskLogPolicy.install(isDiagnosticBuild = false)
    }

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

    @Test
    fun `debug storage preserves unicode and embedded newlines as one json document`() {
        val file = File(tempDir, "gate")
        val key = "sender=中国电信|body=第一行\n第二行"

        assertTrue(SharedRuntimeGate.claimWithinWindow(file, key, 60_000L).claimed)
        val repeated = SharedRuntimeGate.claimWithinWindow(file, key, 60_000L)
        val stored = file.readText()

        assertFalse(repeated.claimed)
        assertEquals(key, repeated.key)
        assertEquals(1, file.readLines().size)
        assertTrue(stored.contains("中国电信"))
        assertTrue(stored.contains("第一行\\n第二行"))
        assertTrue(stored.contains("\"mode\":\"plaintext\""))
    }

    @Test
    fun `release storage redacts raw keys but preserves exact deduplication`() {
        MagiskLogPolicy.install(isDiagnosticBuild = false)
        val file = File(tempDir, "gate")
        val key = "sender=13800138000|body=验证码 064555"

        assertTrue(SharedRuntimeGate.claimWithinWindow(file, key, 60_000L).claimed)
        val repeated = SharedRuntimeGate.claimWithinWindow(file, key, 60_000L)
        val stored = file.readText()

        assertFalse(repeated.claimed)
        assertTrue(repeated.key.orEmpty().startsWith("sha256:"))
        assertFalse(stored.contains("13800138000"))
        assertFalse(stored.contains("064555"))
        assertTrue(stored.contains("\"mode\":\"redacted\""))
        assertTrue(stored.contains("sha256:"))
    }

    @Test
    fun `switching from debug to release drops plaintext history before rewrite`() {
        val file = File(tempDir, "gate")
        val key = "body=secret verification text"
        assertTrue(SharedRuntimeGate.claimWithinWindow(file, key, 60_000L).claimed)
        assertTrue(file.readText().contains(key))

        MagiskLogPolicy.install(isDiagnosticBuild = false)
        assertTrue(SharedRuntimeGate.claimWithinWindow(file, key, 60_000L).claimed)
        assertFalse(file.readText().contains(key))
        assertFalse(SharedRuntimeGate.claimWithinWindow(file, key, 60_000L).claimed)
    }

    @Test
    fun `debug mode migrates valid legacy rows and rewrites json`() {
        val file = File(tempDir, "gate")
        val now = System.currentTimeMillis()
        file.writeText("legacy=key=$now\n")

        val repeated = SharedRuntimeGate.claimWithinWindow(file, "legacy=key", 60_000L)

        assertFalse(repeated.claimed)
        assertEquals("legacy=key", repeated.key)
        assertTrue(file.readText().startsWith("{"))
    }

    @Test
    fun `malformed storage fails open and is replaced with valid json`() {
        val file = File(tempDir, "gate")
        file.writeText("not-json-and-not-a-legacy-row")

        assertTrue(SharedRuntimeGate.claimWithinWindow(file, "fresh", 60_000L).claimed)
        assertTrue(file.readText().startsWith("{"))
    }

    @Test
    fun `oversized storage is reset without allocating the declared file size`() {
        val file = File(tempDir, "gate")
        RandomAccessFile(file, "rw").use { it.setLength(1024L * 1024L + 1L) }

        assertTrue(SharedRuntimeGate.claimWithinWindow(file, "fresh", 60_000L).claimed)
        assertTrue(file.length() < 1024L)
        assertTrue(file.readText().startsWith("{"))
    }
}
