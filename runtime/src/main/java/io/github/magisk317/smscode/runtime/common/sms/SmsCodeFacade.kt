package io.github.magisk317.smscode.runtime.common.sms

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.rule.catalog.SmsCodeRuleMerger
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRefreshResult
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRepository
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSourceKind
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleRemoteSource
import io.github.magisk317.xposed.logging.XLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Host-owned wiring for [SmsCodeFacade]. Only the pieces that genuinely differ per
 * host are injected: preference key names, the user-rule content provider URI, the
 * file-backed fallback loader, the keyword reader, and the userAgent used when the
 * rule catalog downloads remote rule sets.
 */
class SmsCodeFacadeConfig(
    val userAgent: String,
    val sourceUrlPrefKey: String,
    /** Reads the host keyword preference; empty/null means the rule catalog takes over. */
    val keywordReader: (Context) -> String?,
    /** Resolves the host content provider URI serving user rules (company/code_keyword/code_regex). */
    val userRuleContentUri: (Context) -> Uri?,
    /** File-backed fallback when the provider is unavailable or empty. */
    val loadUserRulesFromFile: (Context) -> List<SmsCodeRuleSpec>,
    /** Optional post-refresh notification (e.g. tell hook processes to drop their snapshot). */
    val onOfficialRulesRefreshed: ((Context, SmsCodeRuleCatalogSnapshot) -> Unit)? = null,
)

/**
 * Shared host-side glue between a host app/hook and the smscode parsing engine:
 * user-rule provider access with a file fallback, official catalog snapshot loading
 * and refresh with TTL caching, source-URL preference handling, and the parse entry
 * points via [RuntimeSmsCodeAdapter].
 *
 * Hosts keep thin `SmsCodeUtils` objects with their historical public APIs and
 * delegate here, so both hosts share one implementation instead of drifting.
 */
class SmsCodeFacade(private val config: SmsCodeFacadeConfig) {

    private companion object {
        const val COLUMN_COMPANY = "company"
        const val COLUMN_KEYWORD = "code_keyword"
        const val COLUMN_REGEX = "code_regex"

        /**
         * TTL for the in-process user-rule cache. Each incoming SMS otherwise triggers a
         * cross-process ContentResolver.query to the module app's DBProvider; when that
         * process is frozen (NoActive) the Binder call blocks for tens of seconds and the
         * parse times out. A short cache lets the hook reuse the last rules and keeps the
         * IPC off the per-SMS hot path, while staying fresh enough to pick up edits made
         * in the settings UI a few seconds later (no live listener exists in the hook
         * process, mirroring XposedRuntimeInstaller.SANITIZER_SYNC_TTL_MS).
         */
        const val RULE_CACHE_TTL_MS = 30_000L

        /**
         * TTL for the in-process official-rule snapshot. [loadOfficialRuleSnapshot] walks
         * the on-disk catalog (index + SHA checks + JSON parse) on every SMS merge; when
         * that path is cold or the module files dir is slow it can dominate the 10s parse
         * timeout. Caching the last good snapshot keeps the hot path off the filesystem
         * while still picking up a manual refresh within a minute.
         */
        const val OFFICIAL_RULE_CACHE_TTL_MS = 60_000L
    }

    private val ruleCache = TtlValueCache<List<SmsCodeRuleSpec>>(RULE_CACHE_TTL_MS)

    private class OfficialSnapshotEntry(
        val snapshot: SmsCodeRuleCatalogSnapshot,
        val at: Long,
    )

    // Suspend-safe official snapshot cache: catalog load is suspend I/O, so we cannot
    // park it inside the blocking [TtlValueCache] loader without nested runBlocking.
    private val officialRuleCache = AtomicReference<OfficialSnapshotEntry?>(null)
    private val officialRuleMutex = Mutex()

    private val adapter = RuntimeSmsCodeAdapter(
        keywordProvider = SmsKeywordProvider { context, override ->
            override ?: config.keywordReader(context).orEmpty()
        },
        ruleProvider = SmsCodeRuleProvider { context ->
            loadMergedRuleSpecs(context)
        },
        labelResolver = SmsPackageLabelResolver { context, label ->
            resolvePackageNameByLabel(context, label)
        },
    )

    /**
     * Clears the cached user rules so the next parse re-reads the provider. Call from
     * the module process when the user edits rules, so changes take effect immediately
     * instead of waiting out the TTL.
     */
    fun invalidateUserRuleCache() {
        ruleCache.invalidate()
    }

    /**
     * Drops the cached official-rule snapshot so the next parse reloads from disk /
     * bundled assets. Called after a successful [refreshOfficialRules].
     */
    fun invalidateOfficialRuleCache() {
        officialRuleCache.set(null)
    }

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        keywordsRegexOverride: String? = null,
        source: SmsCodeParseSource? = null,
    ): String = adapter.parseSmsCodeIfExists(context, content, keywordsRegexOverride, source)

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        keywordsRegexOverride: String? = null,
        source: SmsCodeParseSource? = null,
    ): SmsCodeParseResult = adapter.parseSmsCodeResultIfExists(context, content, keywordsRegexOverride, source)

    fun parseCompany(content: String): String = adapter.parseCompany(content)

    fun parseCompanyCandidates(content: String): List<String> = adapter.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return adapter.findPackageNameByLabel(context, label)
    }

    suspend fun loadOfficialRuleSnapshot(context: Context): SmsCodeRuleCatalogSnapshot {
        val now = android.os.SystemClock.elapsedRealtime()
        val hit = officialRuleCache.get()
        if (hit != null && now - hit.at < OFFICIAL_RULE_CACHE_TTL_MS) {
            return hit.snapshot
        }
        return officialRuleMutex.withLock {
            val now2 = android.os.SystemClock.elapsedRealtime()
            val again = officialRuleCache.get()
            if (again != null && now2 - again.at < OFFICIAL_RULE_CACHE_TTL_MS) {
                return@withLock again.snapshot
            }
            val fresh = runCatching {
                catalogRepository(context).loadOfficialRules().also(::logRejectedOfficialRules)
            }.getOrNull()
            if (fresh != null) {
                officialRuleCache.set(OfficialSnapshotEntry(fresh, now2))
                fresh
            } else {
                // Keep last good snapshot across load failures (slow/missing files).
                again?.snapshot ?: SmsCodeRuleCatalogSnapshot(
                    sourceKind = SmsCodeRuleCatalogSourceKind.EMPTY,
                    index = null,
                    rules = emptyList(),
                )
            }
        }
    }

    suspend fun refreshOfficialRules(context: Context): SmsCodeRuleCatalogRefreshResult {
        return catalogRepository(context).refreshOfficialRules().also { result ->
            result.snapshot?.let { snapshot ->
                logRejectedOfficialRules(snapshot)
                // Successful refresh replaces the in-process snapshot immediately.
                officialRuleCache.set(
                    OfficialSnapshotEntry(
                        snapshot = snapshot,
                        at = android.os.SystemClock.elapsedRealtime(),
                    ),
                )
                // Hook processes keep their own official snapshot; signal them to drop it.
                config.onOfficialRulesRefreshed?.invoke(context, snapshot)
            }
            if (!result.success) {
                XLog.w(message = "Refresh official SmsCode rules failed: %s", result.errorMessage ?: "unknown")
            }
        }
    }

    fun observeOfficialRuleSourceUrl(context: Context): Flow<String> =
        AppPreferencesDataStore.getStringFlow(
            context,
            config.sourceUrlPrefKey,
            "",
        )

    suspend fun saveOfficialRuleSourceUrl(context: Context, value: String): String {
        val normalized = SmsCodeRuleRemoteSource.normalizeCustomBaseUrl(value).orEmpty()
        AppPreferencesDataStore.setString(
            context,
            config.sourceUrlPrefKey,
            normalized,
        )
        invalidateOfficialRuleCache()
        return normalized
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun resolvePackageNameByLabel(context: Context, label: String): String? {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            val targetLabel = label.trim()
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().trim().equals(targetLabel, ignoreCase = true)) {
                    return app.packageName
                }
            }
            val normalizedTarget = normalizeLabelForMatching(targetLabel)
            if (normalizedTarget.isBlank()) return null
            for (app in apps) {
                val appLabel = pm.getApplicationLabel(app).toString().trim()
                val normalizedAppLabel = normalizeLabelForMatching(appLabel)
                if (normalizedAppLabel.isBlank()) continue
                if (
                    normalizedAppLabel == normalizedTarget ||
                    normalizedAppLabel.contains(normalizedTarget) ||
                    normalizedTarget.contains(normalizedAppLabel)
                ) {
                    return app.packageName
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeLabelForMatching(label: String): String {
        return label
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}·、，。！（）【】《》「」『』：；]+"), "")
    }

    private fun queryUserRules(context: Context): List<SmsCodeRuleSpec> {
        return ruleCache.get { queryUserRulesUncached(context) } ?: emptyList()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun queryUserRulesUncached(context: Context): List<SmsCodeRuleSpec> {
        val smsCodeRuleUri = try {
            config.userRuleContentUri(context)
        } catch (throwable: Throwable) {
            val fileRules = config.loadUserRulesFromFile(context)
            logProviderFailureFallback(fileRules, throwable)
            return fileRules
        }
        if (smsCodeRuleUri != null) {
            try {
                val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
                context.contentResolver.query(smsCodeRuleUri, projection, null, null, null)?.use { cursor ->
                    val resultRules = mutableListOf<SmsCodeRuleSpec>()
                    while (cursor.moveToNext()) {
                        resultRules.add(
                            SmsCodeRuleSpec(
                                company = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMPANY)),
                                codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)),
                                codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REGEX)),
                            ),
                        )
                    }
                    if (resultRules.isNotEmpty()) {
                        XLog.d("Load SmsCode rules succeed by content provider")
                        return resultRules
                    }
                    return config.loadUserRulesFromFile(context).also(::logProviderEmptyFallback)
                }
                throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
            } catch (throwable: Exception) {
                val fileRules = config.loadUserRulesFromFile(context)
                logProviderFailureFallback(fileRules, throwable)
                return fileRules
            }
        }
        val fileRules = config.loadUserRulesFromFile(context)
        logProviderFailureFallback(fileRules, IllegalStateException("User rule content URI unavailable"))
        return fileRules
    }

    private fun logProviderEmptyFallback(fileRules: List<SmsCodeRuleSpec>) {
        if (fileRules.isEmpty()) {
            XLog.d("Load SmsCode rules by file: provider empty and no persisted user rules")
        } else {
            XLog.w(
                message = "Load SmsCode rules by file: provider empty but %d persisted user rule(s) found",
                fileRules.size,
            )
        }
    }

    private fun logProviderFailureFallback(fileRules: List<SmsCodeRuleSpec>, throwable: Throwable) {
        if (fileRules.isEmpty()) {
            XLog.d(
                message = "Load SmsCode rules by file after provider failure: no persisted user rules, err=%s",
                throwable.message ?: throwable.javaClass.simpleName,
            )
        } else {
            XLog.w(
                message = "Load SmsCode rules by file after provider failure: %d persisted user rule(s) found, err=%s",
                fileRules.size,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private suspend fun loadMergedRuleSpecs(context: Context): List<SmsCodeRuleSpec> {
        val userRules = queryUserRules(context)
        val officialSnapshot = loadOfficialRuleSnapshot(context)
        return SmsCodeRuleMerger.merge(
            userRules = userRules,
            officialRules = officialSnapshot.rules,
        )
    }

    private suspend fun catalogRepository(context: Context): SmsCodeRuleCatalogRepository {
        val appContext = context.applicationContext ?: context
        val customBaseUrl = AppPreferencesDataStore.getString(
            appContext,
            config.sourceUrlPrefKey,
            "",
        )
        return SmsCodeRuleCatalogRepository(
            context = appContext,
            remote = SmsCodeRuleRemoteSource(customBaseUrl = customBaseUrl),
            userAgent = config.userAgent,
        )
    }

    private fun logRejectedOfficialRules(snapshot: SmsCodeRuleCatalogSnapshot) {
        if (snapshot.rejectedRules.isEmpty()) return
        XLog.w(
            message = "Official SmsCode rules rejected: source=%s count=%d first=%s",
            snapshot.sourceKind.name,
            snapshot.rejectedRules.size,
            snapshot.rejectedRules.first(),
        )
    }
}
