package io.github.magisk317.smscode.runtime.common.process

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneProcessRestartCoordinator(
    targetProcesses: Collection<String>,
    private val logger: Logger = Logger.NONE,
    private val commandRunner: CommandRunner = ProcessCommandRunner,
) {
    private val targetProcesses = targetProcesses.filter(String::isNotBlank).distinct()
    private val restartLock = Any()

    init {
        require(this.targetProcesses.isNotEmpty()) { "At least one target process is required." }
    }

    fun requestAfterInstallOrUpdate(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            restartAfterInstallOrUpdate(appContext)
        }
    }

    fun restartAfterInstallOrUpdate(context: Context) {
        synchronized(restartLock) {
            val appContext = context.applicationContext
            val installToken = buildInstallToken(appContext) ?: return
            val prefs = appContext.getSharedPreferences(INSTALL_GUARD_PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_HANDLED_INSTALL_TOKEN, null) == installToken) return

            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_RESTART_ATTEMPT_AT, 0L) < RESTART_ATTEMPT_COOLDOWN_MS) return
            prefs.edit().putLong(KEY_LAST_RESTART_ATTEMPT_AT, now).apply()

            if (isPhoneCallActive(appContext)) {
                logger.info("Skip target process restart because call state is active.")
                return
            }
            if (!canUseRoot()) {
                logger.warn("Target process restart deferred after install/update: root unavailable.")
                return
            }

            val restartResult = restartTargetProcessesViaRoot()
            if (restartResult.shouldMarkInstallHandled) {
                prefs.edit().putString(KEY_LAST_HANDLED_INSTALL_TOKEN, installToken).apply()
                logger.info(
                    "Target process restart completed after install/update change: " +
                        "found=${restartResult.summary?.found ?: 0} killed=${restartResult.summary?.killed ?: 0}",
                )
            } else {
                logger.warn(
                    "Target process restart still pending after install/update change: " +
                        "exit=${restartResult.exitCode} output=${restartResult.rawOutput.trim()}",
                )
            }
        }
    }

    private fun buildInstallToken(context: Context): String? {
        val packageInfo = runCatching { getSelfPackageInfo(context) }.getOrNull() ?: return null
        val apkFile = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull() ?: return null
        return listOf(
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            runCatching(apkFile::length).getOrDefault(0L),
            runCatching(apkFile::lastModified).getOrDefault(0L),
        ).joinToString(separator = ":")
    }

    private fun getSelfPackageInfo(context: Context): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    private fun canUseRoot(): Boolean {
        val result = commandRunner.run("id -u", SU_COMMAND_TIMEOUT_SEC)
        return result.exitCode == 0 && result.output.trim() == "0"
    }

    private fun isPhoneCallActive(context: Context): Boolean {
        val telephonyInCall = runCatching {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val state = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(false)
        if (telephonyInCall) return true

        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    private fun restartTargetProcessesViaRoot(): RestartAttemptResult {
        val result = commandRunner.run(buildRestartCommand(targetProcesses), SU_COMMAND_TIMEOUT_SEC)
        return RestartAttemptResult(
            exitCode = result.exitCode,
            summary = parseRestartCommandSummary(result.output),
            rawOutput = result.output,
        )
    }

    interface Logger {
        fun info(message: String)

        fun warn(message: String)

        companion object {
            val NONE = object : Logger {
                override fun info(message: String) = Unit
                override fun warn(message: String) = Unit
            }
        }
    }

    fun interface CommandRunner {
        fun run(command: String, timeoutSeconds: Long): CommandResult
    }

    data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    data class RestartCommandSummary(
        val found: Int,
        val killed: Int,
        val failed: Int,
    )

    data class RestartAttemptResult(
        val exitCode: Int,
        val summary: RestartCommandSummary?,
        val rawOutput: String,
    ) {
        val shouldMarkInstallHandled: Boolean
            get() = exitCode == 0 && summary != null && summary.failed == 0
    }

    companion object {
        private const val INSTALL_GUARD_PREFS = "install_guard_prefs"
        private const val KEY_LAST_HANDLED_INSTALL_TOKEN = "last_handled_install_token"
        private const val KEY_LAST_RESTART_ATTEMPT_AT = "last_restart_attempt_at"
        private const val RESTART_ATTEMPT_COOLDOWN_MS = 60_000L
        private const val SU_COMMAND_TIMEOUT_SEC = 10L
        private val restartSummaryRegex = Regex("""found=(\d+)\s+killed=(\d+)\s+failed=(\d+)""")

        fun parseRestartCommandSummary(output: String): RestartCommandSummary? {
            val match = restartSummaryRegex.find(output) ?: return null
            return RestartCommandSummary(
                found = match.groupValues[1].toInt(),
                killed = match.groupValues[2].toInt(),
                failed = match.groupValues[3].toInt(),
            )
        }

        internal fun buildRestartCommand(targetProcesses: Collection<String>): String {
            val targets = targetProcesses.joinToString(separator = " ") { shellQuote(it) }
            return "FOUND=0; KILLED=0; FAILED=0; " +
                "for NAME in $targets; do " +
                "PIDS=\$(pidof \"${'$'}NAME\" 2>/dev/null); " +
                "if [ -n \"${'$'}PIDS\" ]; then " +
                "FOUND=\$((FOUND + 1)); " +
                "if kill -9 ${'$'}PIDS >/dev/null 2>&1; then " +
                "KILLED=\$((KILLED + 1)); " +
                "else FAILED=\$((FAILED + 1)); fi; " +
                "fi; " +
                "done; " +
                "echo found=${'$'}FOUND killed=${'$'}KILLED failed=${'$'}FAILED; " +
                "if [ \"${'$'}FAILED\" -gt 0 ]; then exit 1; fi; " +
                "exit 0"
        }

        private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }

    private object ProcessCommandRunner : CommandRunner {
        override fun run(command: String, timeoutSeconds: Long): CommandResult {
            return try {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroy()
                    if (process.isAlive) process.destroyForcibly()
                    CommandResult(exitCode = -2, output = "")
                } else {
                    CommandResult(
                        exitCode = process.exitValue(),
                        output = process.inputStream.bufferedReader().use { it.readText() },
                    )
                }
            } catch (_: Throwable) {
                CommandResult(exitCode = -1, output = "")
            }
        }
    }
}
