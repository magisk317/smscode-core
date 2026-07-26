package io.github.magisk317.smscode.xposed.utils

import android.content.Context
import io.github.magisk317.xposed.logging.MagiskOtel
import java.io.File
import java.io.IOException

object ModuleActivationStore {
    private const val FILE_NAME = "module_activated"
    private const val MAX_ACTIVE_AGE_MS = 24 * 60 * 60 * 1000L
    private const val NANOS_PER_MILLI = 1_000_000L

    fun markActivated(context: Context) {
        val startedAt = System.nanoTime()
        val file = getStoreFile(context)
        val ok = try {
            file.parentFile?.mkdirs()
            file.writeText(System.currentTimeMillis().toString())
            true
        } catch (_: IOException) {
            false
        }
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to if (ok) "ok" else "error",
                "duration_ms" to (((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)).toString(),
                "process" to "hook",
                "stage" to "activation_mark",
                "reason" to if (ok) "written" else "write_failed",
            ),
            statusOk = ok,
        )
    }

    fun isActivatedRecently(context: Context): Boolean {
        val last = getLastActivated(context)
        val recent = last > 0L && System.currentTimeMillis() - last <= MAX_ACTIVE_AGE_MS
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to if (recent) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "activation_check",
                "reason" to if (recent) "recent" else if (last <= 0L) "missing" else "stale",
            ),
            statusOk = true,
        )
        return recent
    }

    private fun getLastActivated(context: Context): Long {
        val file = getStoreFile(context)
        return try {
            val text = file.readText().trim()
            text.toLongOrNull() ?: file.lastModified()
        } catch (_: IOException) {
            0L
        }
    }

    private fun getStoreFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }
}
