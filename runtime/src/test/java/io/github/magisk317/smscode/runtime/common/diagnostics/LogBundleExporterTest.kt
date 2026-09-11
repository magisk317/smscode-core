package io.github.magisk317.smscode.runtime.common.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LogBundleExporterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun redundantAppRouteRuntimeLog_isDetectedByDailyRouteNameOnly() {
        assertTrue(
            LogBundleExporter.isRedundantAppRouteRuntimeLog(
                File(tempDir, "runtime.app.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
        assertFalse(
            LogBundleExporter.isRedundantAppRouteRuntimeLog(
                File(tempDir, "runtime.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
        assertFalse(
            LogBundleExporter.isRedundantAppRouteRuntimeLog(
                File(tempDir, "runtime.sms_hook.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
    }

    @Test
    fun deleteLegacyTextLogFiles_removesOldTextLogsOnly() {
        val logDir = File(tempDir, "log").apply { mkdirs() }
        val moduleDir = File(logDir, "modules").apply { mkdirs() }
        val runtimeText = File(logDir, "runtime.log").apply { writeText("old") }
        val appText = File(logDir, "logs_2026-06-06.txt").apply { writeText("old") }
        val routeText = File(moduleDir, "runtime.sms_hook_2026-06-06.txt").apply { writeText("old") }
        val jsonl = File(logDir, "runtime.2026-06-06.jsonl").apply { writeText("{}") }

        val deleted = LogBundleExporter.deleteLegacyTextLogFiles(logDir)

        assertEquals(3, deleted)
        assertFalse(runtimeText.exists())
        assertFalse(appText.exists())
        assertFalse(routeText.exists())
        assertTrue(jsonl.exists())
    }
}
