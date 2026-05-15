package io.github.magisk317.smscode.runtime.common.diagnostics

import android.content.Context
import android.util.Log
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RuntimeLogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String,
    val message: String,
    val route: String = RuntimeLogStore.ROUTE_APP,
    val processName: String = "",
    val pid: Int = 0,
    val uid: Int = 0,
    val threadName: String = "",
    val threadId: Long = 0L,
)

data class RuntimeLogFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lineCount: Int,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
)

data class RuntimeLogFileSummary(
    val fileCount: Int,
    val totalBytes: Long,
    val entryCount: Int,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
    val files: List<RuntimeLogFileInfo>,
)

data class RuntimeLogFileContent(
    val name: String,
    val sizeBytes: Long,
    val displayedLineCount: Int,
    val truncated: Boolean,
    val text: String,
)

object RuntimeLogStore {
    private const val INTERNAL_TAG = "RuntimeLogStore"
    private const val LOG_FILE_NAME = "runtime.jsonl"
    private const val MAX_BUFFER_SIZE = 1200
    private const val DEFAULT_RETENTION_DAYS = 2
    private const val MIN_RETENTION_DAYS = 1
    private const val RETENTION_REFRESH_INTERVAL_MS = 5_000L
    private const val RETENTION_PRUNE_INTERVAL_MS = 60_000L
    private const val MAX_READ_LINES = 2000
    const val ROUTE_SMS_HOOK = "sms_hook"
    const val ROUTE_NMS_HOOK = "nms_hook"
    const val ROUTE_SYSTEM_INPUT = "system_input"
    const val ROUTE_PERMISSION_HOOK = "permission_hook"
    const val ROUTE_FORWARD = "forward"
    const val ROUTE_SENDER = "sender"
    const val ROUTE_ROOT_DB = "root_db"
    const val ROUTE_APP = "app"
    private const val FILE_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
    private const val FILE_DATE_PATTERN = "yyyy-MM-dd"

    private val lock = Any()
    private val buffer = ArrayDeque<RuntimeLogEntry>(MAX_BUFFER_SIZE)
    private val pendingFileEntries = ArrayDeque<RuntimeLogEntry>(MAX_BUFFER_SIZE)
    private val dailyRuntimeLogRegex = Regex("""^runtime\.\d{4}-\d{2}-\d{2}\.jsonl$""")
    private val dailyLogDateRegex = Regex("""^runtime(?:\.[^.]+)?\.(\d{4}-\d{2}-\d{2})\.jsonl$""")
    private val jsonLineParser = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }
    private val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "runtime-log-flusher").apply { isDaemon = true }
    }
    private val fileTimestampFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.getDefault())
    }
    private val fileDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat(FILE_DATE_PATTERN, Locale.US).apply {
            isLenient = false
        }
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var retentionDays: Int = DEFAULT_RETENTION_DAYS

    @Volatile
    private var lastRetentionRefreshAtMs: Long = 0L

    @Volatile
    private var lastRetentionPruneAtMs: Long = 0L

    private fun isModuleContext(context: Context?): Boolean {
        val applicationId = RuntimeDiagnosticsEnvironment.current().applicationId
        return applicationId.isNotBlank() && context?.packageName == applicationId
    }

    fun initialize(context: Context, enableDetailedLogs: Boolean) {
        val resolvedContext = resolveModuleContext(context)
        appContext = resolvedContext.takeIf { isModuleContext(it) }
        enabled = enableDetailedLogs
        setRetentionDays(readConfiguredRetentionDays(appContext ?: context))
        deleteLegacyTextLogFiles(appContext ?: context)
        flushPendingIfPossible()
    }

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    fun isEnabled(): Boolean = enabled

    fun setRetentionDays(days: Int) {
        retentionDays = days.coerceAtLeast(MIN_RETENTION_DAYS)
    }

    @Deprecated("Use setRetentionDays; runtime logs now rotate by day.")
    fun setMaxFileSizeMb(sizeMb: Int) {
        setRetentionDays(sizeMb)
    }

    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = null,
    ) {
        if (!enabled && !force) return
        runCatching {
            val normalizedRoute = normalizeRoute(route)
            val thread = Thread.currentThread()
            val entry = RuntimeLogEntry(
                timestamp = System.currentTimeMillis(),
                priority = priority,
                tag = tag,
                message = message,
                route = normalizedRoute,
                processName = currentProcessName(),
                pid = android.os.Process.myPid(),
                uid = android.os.Process.myUid(),
                threadName = thread.name.orEmpty(),
                threadId = currentThreadId(thread),
            )
            synchronized(lock) {
                buffer.addLast(entry)
                while (buffer.size > MAX_BUFFER_SIZE) {
                    buffer.removeFirst()
                }
                pendingFileEntries.addLast(entry)
                while (pendingFileEntries.size > MAX_BUFFER_SIZE) {
                    pendingFileEntries.removeFirst()
                }
            }
            triggerFlush()
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "append skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            pendingFileEntries.clear()
        }
        val logDir = getLogDir() ?: return
        runCatching {
            logDir.listFiles()
                ?.filter { isRuntimeLogFileName(it.name) || isLegacyTextLogFileName(it.name) }
                ?.forEach { file ->
                    if (!file.delete()) {
                        file.writeText("")
                    }
                }
        }
    }

    fun query(minutes: Int?, keyword: String?, limit: Int = 600): List<RuntimeLogEntry> {
        val memoryList = synchronized(lock) { buffer.toList() }
        val source = (readFromFiles() + memoryList)
            .distinctBy { entry -> "${entry.timestamp}:${entry.priority}:${entry.tag}:${entry.route}:${entry.message}" }
            .sortedBy { it.timestamp }
        val now = System.currentTimeMillis()
        val cutoff = if (minutes == null || minutes <= 0) Long.MIN_VALUE else now - minutes * 60_000L
        val normalizedKeyword = keyword?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return source.asSequence()
            .filter { it.timestamp >= cutoff }
            .filter {
                if (normalizedKeyword.isEmpty()) {
                    true
                } else {
                    it.tag.lowercase(Locale.ROOT).contains(normalizedKeyword) ||
                        it.message.lowercase(Locale.ROOT).contains(normalizedKeyword)
                }
            }
            .toList()
            .takeLast(limit)
    }

    fun exportText(minutes: Int?, keyword: String?, limit: Int = 600): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val lines = query(minutes = minutes, keyword = keyword, limit = limit).map { entry ->
            val ts = formatter.format(Date(entry.timestamp))
            "$ts [${priorityName(entry.priority)}/${entry.tag}] ${entry.message}"
        }
        return lines.joinToString(separator = "\n\n")
    }

    fun exportToFile(context: Context, minutes: Int?, keyword: String?, limit: Int = 1200): File? {
        val dir = StorageUtils.getPrivateLogExportDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null

        val fileName = "runtime_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val file = File(dir, fileName)
        val content = exportText(minutes = minutes, keyword = keyword, limit = limit)
        return runCatching {
            file.writeText(content.ifBlank { "No logs." })
            StorageUtils.setFileWorldReadable(file, 2)
            file
        }.getOrNull()
    }

    fun summarizeFiles(): RuntimeLogFileSummary {
        val files = getRuntimeLogFiles()
        val infos = files.map { summarizeFile(it) }
        return RuntimeLogFileSummary(
            fileCount = infos.size,
            totalBytes = infos.sumOf { it.sizeBytes },
            entryCount = infos.sumOf { it.lineCount },
            firstTimestamp = infos.mapNotNull { it.firstTimestamp }.minOrNull(),
            lastTimestamp = infos.mapNotNull { it.lastTimestamp }.maxOrNull(),
            files = infos,
        )
    }

    fun readLogFile(name: String, maxLines: Int = MAX_READ_LINES): RuntimeLogFileContent? {
        val safeName = name.trim()
        if (safeName.isBlank() || safeName != File(safeName).name) return null
        val file = getRuntimeLogFiles().firstOrNull { it.name == safeName } ?: return null
        val lineLimit = maxLines.coerceAtLeast(1)
        val lines = readRecentLines(file, lineLimit)
        return RuntimeLogFileContent(
            name = file.name,
            sizeBytes = file.length(),
            displayedLineCount = lines.size,
            truncated = lines.size >= lineLimit && file.length() > 0L,
            text = lines.joinToString(separator = "\n"),
        )
    }

    fun deleteLegacyTextLogFiles(context: Context? = null): Int {
        val logDir = context?.let { StorageUtils.getLogDir(it) } ?: getLogDir() ?: return 0
        var deleted = 0
        runCatching {
            logDir.listFiles()
                .orEmpty()
                .filter { it.isFile && isLegacyTextLogFileName(it.name) }
                .forEach { file ->
                    if (file.delete()) {
                        deleted += 1
                    } else {
                        file.writeText("")
                        deleted += 1
                    }
                }
        }
        return deleted
    }

    private fun readFromFiles(): List<RuntimeLogEntry> {
        val files = getPrimaryLogFiles()
        if (files.isEmpty()) return emptyList()
        return runCatching {
            files.flatMap { file ->
                readRecentLines(file, MAX_READ_LINES)
            }
                .mapNotNull { decode(it) }
                .sortedBy { it.timestamp }
                .takeLast(MAX_READ_LINES)
        }.getOrDefault(emptyList())
    }

    private fun readRecentLines(file: File, maxLines: Int): List<String> {
        return runCatching {
            val lines = ArrayDeque<String>(maxLines.coerceAtLeast(1))
            file.bufferedReader().useLines { sequence ->
                sequence.forEach { line ->
                    lines.addLast(line)
                    while (lines.size > maxLines) {
                        lines.removeFirst()
                    }
                }
            }
            lines.toList()
        }.getOrDefault(emptyList())
    }

    private fun getPrimaryLogFiles(): List<File> {
        return getRuntimeLogFiles()
            .filter { isPrimaryRuntimeLogFileName(it.name) }
            .sortedWith(compareBy<File> { runtimeLogSortTime(it) }.thenBy { it.name })
    }

    private fun getRuntimeLogFiles(): List<File> {
        val context = ensureAppContext() ?: return emptyList()
        val dir = StorageUtils.getLogDir(context) ?: return emptyList()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && isRuntimeLogFileName(it.name) }
            .sortedWith(compareBy<File> { runtimeLogSortTime(it) }.thenBy { it.name })
    }

    private fun summarizeFile(file: File): RuntimeLogFileInfo {
        var lineCount = 0
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val entry = decode(line) ?: return@forEach
                    lineCount += 1
                    if (firstTimestamp == null || entry.timestamp < firstTimestamp!!) {
                        firstTimestamp = entry.timestamp
                    }
                    if (lastTimestamp == null || entry.timestamp > lastTimestamp!!) {
                        lastTimestamp = entry.timestamp
                    }
                }
            }
        }
        return RuntimeLogFileInfo(
            name = file.name,
            sizeBytes = file.length(),
            lineCount = lineCount,
            firstTimestamp = firstTimestamp,
            lastTimestamp = lastTimestamp,
        )
    }

    private fun getLogDir(): File? {
        val context = ensureAppContext() ?: return null
        return StorageUtils.getLogDir(context)
    }

    private fun ensureAppContext(): Context? = synchronized(lock) {
        val existing = appContext
        if (isModuleContext(existing)) {
            return@synchronized existing
        }
        val resolved = resolveContextFromActivityThreadLocked() ?: return@synchronized null
        if (!isModuleContext(resolved)) {
            return@synchronized null
        }
        appContext = resolved
        return@synchronized resolved
    }

    private fun resolveContextFromActivityThreadLocked(): Context? {
        val application = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
            currentApplication.isAccessible = true
            currentApplication.invoke(null) as? Context
        }.getOrNull() ?: return null
        return resolveModuleContext(application)
    }

    private fun resolveModuleContext(context: Context): Context {
        val app = context.applicationContext ?: context
        val applicationId = RuntimeDiagnosticsEnvironment.current().applicationId
        if (applicationId.isBlank()) return app
        if (app.packageName == applicationId) return app

        // Skip direct file writing for system_server or processes with different UIDs
        // as they likely won't have permission to write to the app's data directory.
        if (android.os.Process.myUid() == 1000 || android.os.Process.myUid() == 0) {
            return app
        }

        return runCatching {
            app.createPackageContext(applicationId, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse { app }
    }

    private fun triggerFlush() {
        logExecutor.execute {
            synchronized(lock) {
                flushPendingLocked()
            }
        }
    }

    private fun appendToFilesLocked(entry: RuntimeLogEntry): Boolean {
        return runCatching {
            val context = appContext ?: resolveContextFromActivityThreadLocked() ?: return false
            if (!isModuleContext(context)) return false
            if (appContext == null) {
                appContext = context
            }
            maybeRefreshRetentionLocked(context)
            val logDir = StorageUtils.getLogDir(context) ?: return false
            val line = encode(entry) + "\n"
            writeLineToFile(logDir, LOG_FILE_NAME, entry.timestamp, line)
            writeLineToFile(logDir, routeFileName(entry.route), entry.timestamp, line)
            true
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "appendToFilesLocked skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }.getOrDefault(false)
    }

    private fun flushPendingIfPossible() {
        synchronized(lock) {
            flushPendingLocked()
        }
    }

    private fun flushPendingLocked() {
        if (pendingFileEntries.isEmpty()) return
        runCatching {
            val context = appContext ?: resolveContextFromActivityThreadLocked() ?: return
            if (!isModuleContext(context)) return
            if (appContext == null) {
                appContext = context
            }
            maybeRefreshRetentionLocked(context)
            val logDir = StorageUtils.getLogDir(context) ?: return
            maybePruneExpiredLogsLocked(logDir, System.currentTimeMillis())
            while (pendingFileEntries.isNotEmpty()) {
                val entry = pendingFileEntries.removeFirst()
                val line = encode(entry) + "\n"
                writeLineToFile(logDir, LOG_FILE_NAME, entry.timestamp, line)
                writeLineToFile(logDir, routeFileName(entry.route), entry.timestamp, line)
            }
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "flushPendingLocked skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }
    }

    private fun writeLineToFile(logDir: File, baseFileName: String, timestamp: Long, line: String) {
        runCatching {
            val file = File(logDir, dailyLogFileName(baseFileName, timestamp))
            ensureParentDir(file)
            file.appendText(line)
            StorageUtils.setFileWorldReadable(file, 2)
            StorageUtils.setFileWorldWritable(file, 1)
        }
    }

    private fun ensureParentDir(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
    }

    private fun maybeRefreshRetentionLocked(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRetentionRefreshAtMs < RETENTION_REFRESH_INTERVAL_MS) return
        lastRetentionRefreshAtMs = now
        setRetentionDays(readConfiguredRetentionDays(context))
    }

    private fun readConfiguredRetentionDays(context: Context): Int {
        val config = RuntimeDiagnosticsEnvironment.current()
        @Suppress("DEPRECATION")
        return config.logRetentionDaysProvider?.invoke(context)
            ?: config.maxLogFileSizeMbProvider?.invoke(context)
            ?: DEFAULT_RETENTION_DAYS
    }

    private fun maybePruneExpiredLogsLocked(logDir: File, now: Long) {
        if (now - lastRetentionPruneAtMs < RETENTION_PRUNE_INTERVAL_MS) return
        lastRetentionPruneAtMs = now
        val cutoff = retentionCutoffStartMs(now)
        logDir.listFiles()
            .orEmpty()
            .filter { it.isFile && isRuntimeLogFileName(it.name) }
            .forEach { file ->
                val fileTime = dailyLogDateStartMs(file.name) ?: file.lastModified()
                if (fileTime > 0L && fileTime < cutoff) {
                    runCatching { file.delete() }
                }
            }
    }

    private fun retentionCutoffStartMs(now: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(retentionDays.coerceAtLeast(MIN_RETENTION_DAYS) - 1))
        }.timeInMillis
    }

    private fun dailyLogFileName(baseFileName: String, timestamp: Long): String {
        val date = fileDateFormatter.get()?.format(Date(timestamp)) ?: timestamp.toString()
        val stem = baseFileName
            .removeSuffix(".jsonl")
            .removeSuffix(".log")
        return "$stem.$date.jsonl"
    }

    private fun isRuntimeLogFileName(name: String): Boolean {
        return name == LOG_FILE_NAME ||
            (name.startsWith("runtime.") && name.endsWith(".jsonl"))
    }

    private fun isPrimaryRuntimeLogFileName(name: String): Boolean {
        return name == LOG_FILE_NAME || dailyRuntimeLogRegex.matches(name)
    }

    private fun isLegacyTextLogFileName(name: String): Boolean {
        return name == "runtime.log" || (name.startsWith("runtime.") && name.endsWith(".log"))
    }

    private fun runtimeLogSortTime(file: File): Long {
        return dailyLogDateStartMs(file.name) ?: file.lastModified()
    }

    private fun dailyLogDateStartMs(fileName: String): Long? {
        val value = dailyLogDateRegex.matchEntire(fileName)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { fileDateFormatter.get()?.parse(value)?.time }.getOrNull()
    }

    private fun encode(entry: RuntimeLogEntry): String {
        return buildJsonObject {
            put("timestamp", entry.timestamp)
            put("time", formatFileTimestamp(entry.timestamp))
            put("priority", entry.priority)
            put("level", priorityName(entry.priority))
            put("tag", entry.tag)
            put("message", entry.message)
            put("route", entry.route)
            put("process", entry.processName)
            put("pid", entry.pid)
            put("uid", entry.uid)
            put("thread", entry.threadName)
            put("threadId", entry.threadId)
        }.toString()
    }

    private fun decode(line: String): RuntimeLogEntry? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{")) return null
        return decodeJsonLine(trimmed)
    }

    private fun decodeJsonLine(line: String): RuntimeLogEntry? {
        return runCatching {
            val jsonObject = jsonLineParser.parseToJsonElement(line).jsonObject
            val timestamp = jsonObject["timestamp"]?.jsonPrimitive?.longOrNull
                ?: jsonObject["ts"]?.jsonPrimitive?.longOrNull
                ?: return null
            val priority = jsonObject["priority"]?.jsonPrimitive?.intOrNull
                ?: priorityFromName(jsonObject["level"]?.jsonPrimitive?.contentOrNull)
                ?: Log.INFO
            RuntimeLogEntry(
                timestamp = timestamp,
                priority = priority,
                tag = jsonObject["tag"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                message = jsonObject["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                route = normalizeRoute(jsonObject["route"]?.jsonPrimitive?.contentOrNull),
                processName = jsonObject["process"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                pid = jsonObject["pid"]?.jsonPrimitive?.intOrNull ?: 0,
                uid = jsonObject["uid"]?.jsonPrimitive?.intOrNull ?: 0,
                threadName = jsonObject["thread"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                threadId = jsonObject["threadId"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.getOrNull()
    }

    private fun formatFileTimestamp(timestamp: Long): String {
        return fileTimestampFormatter.get()?.format(Date(timestamp)) ?: timestamp.toString()
    }

    private fun currentProcessName(): String {
        return runCatching {
            android.app.Application.getProcessName()
        }.getOrNull().orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun currentThreadId(thread: Thread): Long {
        return thread.id
    }

    private fun priorityName(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
    }

    private fun priorityFromName(level: String?): Int? {
        return when (level?.uppercase(Locale.ROOT)) {
            "V", "VERBOSE" -> Log.VERBOSE
            "D", "DEBUG" -> Log.DEBUG
            "I", "INFO" -> Log.INFO
            "W", "WARN" -> Log.WARN
            "E", "ERROR" -> Log.ERROR
            "A", "ASSERT" -> Log.ASSERT
            else -> level?.toIntOrNull()
        }
    }

    private fun normalizeRoute(route: String?): String {
        val normalized = route?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (normalized) {
            ROUTE_SMS_HOOK,
            ROUTE_NMS_HOOK,
            ROUTE_SYSTEM_INPUT,
            ROUTE_PERMISSION_HOOK,
            ROUTE_FORWARD,
            ROUTE_SENDER,
            ROUTE_ROOT_DB,
            ROUTE_APP,
            -> normalized
            else -> ROUTE_APP
        }
    }

    private fun routeFileName(route: String): String {
        return when (normalizeRoute(route)) {
            ROUTE_SMS_HOOK -> "runtime.sms_hook.jsonl"
            ROUTE_NMS_HOOK -> "runtime.nms_hook.jsonl"
            ROUTE_SYSTEM_INPUT -> "runtime.system_input.jsonl"
            ROUTE_PERMISSION_HOOK -> "runtime.permission_hook.jsonl"
            ROUTE_FORWARD -> "runtime.forward.jsonl"
            ROUTE_SENDER -> "runtime.sender.jsonl"
            ROUTE_ROOT_DB -> "runtime.root_db.jsonl"
            else -> "runtime.app.jsonl"
        }
    }

    fun routeFromCallerClassName(className: String?): String {
        return RuntimeDiagnosticsEnvironment.current().routeResolver?.invoke(className) ?: ROUTE_APP
    }
}
