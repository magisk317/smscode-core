package io.github.magisk317.smscode.runtime.common.diagnostics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeDiagnosticsInstallerTest {
    @Test
    fun `concurrent callers install one configuration`() {
        val installCount = AtomicInteger()
        val installer = RuntimeDiagnosticsInstaller(
            configProvider = { RuntimeDiagnosticsConfig(applicationId = "test") },
            install = { installCount.incrementAndGet() },
        )
        val executor = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(32)

        repeat(32) {
            executor.execute {
                installer.ensureInstalled()
                done.countDown()
            }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, installCount.get())
    }
}
