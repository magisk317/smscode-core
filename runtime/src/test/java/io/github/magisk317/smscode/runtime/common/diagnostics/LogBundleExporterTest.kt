package io.github.magisk317.smscode.runtime.common.diagnostics

import io.github.magisk317.xposed.diagnostics.DiagnosticBuildStamps
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleIdentity
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun redundantRoutedRuntimeLog_isDetectedByDailyRouteNameOnly() {
        assertTrue(
            RuntimeLogStore.isRedundantRoutedRuntimeLog(
                File(tempDir, "runtime.app.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
        assertTrue(
            RuntimeLogStore.isRedundantRoutedRuntimeLog(
                File(tempDir, "runtime.sms_hook.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
        assertFalse(
            RuntimeLogStore.isRedundantRoutedRuntimeLog(
                File(tempDir, "runtime.2026-06-06.jsonl").apply { writeText("{}") },
            ),
        )
        assertFalse(
            RuntimeLogStore.isRedundantRoutedRuntimeLog(
                File(tempDir, "runtime.sender.jsonl").apply { writeText("{}") },
            ),
        )
    }

    @Test
    fun deleteRedundantRoutedLogs_removesDailyRouteFilesOnly() {
        val logDir = File(tempDir, "log").apply { mkdirs() }
        val appRoute = File(logDir, "runtime.app.2026-10-20.jsonl").apply { writeText("{}") }
        val unified = File(logDir, "runtime.2026-10-20.jsonl").apply { writeText("{}") }
        val senderRoute = File(logDir, "runtime.sender.2026-10-20.jsonl").apply { writeText("{}") }
        val legacyAppRoute = File(logDir, "runtime.app.jsonl").apply { writeText("{}") }

        val deleted = RuntimeLogStore.deleteRedundantRoutedLogs(logDir)

        assertEquals(2, deleted)
        assertFalse(appRoute.exists())
        assertFalse(senderRoute.exists())
        assertTrue(unified.exists())
        assertTrue(legacyAppRoute.exists())
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

    @Test
    fun buildRuntimeSummaryJson_includesSharedIdentityAndRuntimeStats() {
        val summary = RuntimeLogFileSummary(
            fileCount = 1,
            totalBytes = 128,
            entryCount = 4,
            firstTimestamp = 1_000L,
            lastTimestamp = 2_000L,
            files = listOf(
                RuntimeLogFileInfo(
                    name = "runtime.2026-10-20.jsonl",
                    sizeBytes = 128,
                    lineCount = 4,
                    firstTimestamp = 1_000L,
                    lastTimestamp = 2_000L,
                ),
            ),
        )
        val packageIdentity = RuntimePackageIdentity(
            packageName = "io.github.magisk317.xinyi.relay",
            versionName = "0.4.1",
            versionCode = "54",
            distributionChannel = "sideload",
            commitSha = "23a2e149",
            apkPath = "/data/app/io.github.magisk317.xinyi.relay-1/base.apk",
            apkSha256 = "abc123",
        )

        val json = LogBundleExporter.buildRuntimeSummaryJson(
            summary,
            packageIdentity,
            DiagnosticBundleIdentity.of(
                product = "relay",
                packageName = packageIdentity.packageName,
                versionName = packageIdentity.versionName,
                versionCode = packageIdentity.versionCode,
                distributionChannel = packageIdentity.distributionChannel,
                commitSha = packageIdentity.commitSha,
                libraryCommitShas = mapOf("smscode-core" to "3c956f9"),
            ),
        )
        val firstFile = json["files"]!!.jsonArray.single().jsonObject

        assertEquals("io.github.magisk317.xinyi.relay", json["packageName"]!!.jsonPrimitive.content)
        assertEquals("0.4.1", json["versionName"]!!.jsonPrimitive.content)
        assertEquals("54", json["versionCode"]!!.jsonPrimitive.content)
        assertEquals("sideload", json["distributionChannel"]!!.jsonPrimitive.content)
        assertEquals("23a2e149", json["commitSha"]!!.jsonPrimitive.content)
        assertEquals("abc123", json["apkSha256"]!!.jsonPrimitive.content)
        assertEquals("relay", json["product"]!!.jsonPrimitive.content)
        assertEquals("3c956f9", json["lib_smscode-core"]!!.jsonPrimitive.content)
        assertEquals(
            DiagnosticBuildStamps.kitCommitHash,
            json["kitCommitSha"]!!.jsonPrimitive.content,
        )
        assertEquals(1, json["fileCount"]!!.jsonPrimitive.int)
        assertEquals(4, json["entryCount"]!!.jsonPrimitive.int)
        assertEquals("runtime.2026-10-20.jsonl", firstFile["name"]!!.jsonPrimitive.content)
        assertEquals(4, firstFile["lineCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun packageIdentitySummaryLine_keepsMiPushFieldOrder() {
        val identity = RuntimePackageIdentity(
            packageName = "io.github.magisk317.xinyi.relay",
            versionName = "0.4.1",
            versionCode = "54",
            distributionChannel = "sideload",
            commitSha = "23a2e149",
            apkPath = "/data/app/base.apk",
            apkSha256 = "abc123",
        )

        assertEquals(
            "app package=io.github.magisk317.xinyi.relay versionName=0.4.1 versionCode=54 " +
                "distributionChannel=sideload commitSha=23a2e149 " +
                "apkPath=/data/app/base.apk apkSha256=abc123",
            identity.toSummaryLine(),
        )
    }

    @Test
    fun sha256FileOrUnavailable_hashesApkAndReportsMissingPath() {
        val file = File(tempDir, "base.apk").apply { writeText("abc") }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            LogBundleExporter.sha256FileOrUnavailable(file.absolutePath),
        )
        assertEquals("unavailable:path_missing", LogBundleExporter.sha256FileOrUnavailable(""))
        assertEquals(
            "unavailable:file_missing",
            LogBundleExporter.sha256FileOrUnavailable(File(tempDir, "missing.apk").absolutePath),
        )
    }
}
