package io.github.magisk317.smscode.runtime.common.rules

import android.content.Context
import io.github.magisk317.smscode.runtime.common.serialization.JsonConfig
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlinx.serialization.decodeFromString

class SmsCodeRuleCatalogRepository(
    private val cacheSource: WritableSmsCodeRuleCatalogSource,
    private val bundledSource: SmsCodeRuleCatalogSource,
    private val remoteSource: SmsCodeRuleCatalogSource,
) {
    constructor(
        context: Context,
        remote: SmsCodeRuleRemoteSource = SmsCodeRuleRemoteSource(),
        assetRoot: String = DEFAULT_SMS_CODE_RULES_ASSET_ROOT,
        cacheDir: File = File((context.applicationContext ?: context).filesDir, DEFAULT_SMS_CODE_RULES_ASSET_ROOT),
        userAgent: String = "SmsCodeRules/1",
    ) : this(
        cacheSource = FileSmsCodeRuleCatalogSource(cacheDir),
        bundledSource = AssetSmsCodeRuleCatalogSource(context, assetRoot),
        remoteSource = RemoteSmsCodeRuleCatalogSource(remote, userAgent),
    )

    suspend fun loadOfficialRules(): SmsCodeRuleCatalogSnapshot {
        return loadFrom(cacheSource, SmsCodeRuleCatalogSourceKind.CACHE).getOrElse {
            loadFrom(bundledSource, SmsCodeRuleCatalogSourceKind.BUNDLED).getOrElse {
                SmsCodeRuleCatalogSnapshot(
                    sourceKind = SmsCodeRuleCatalogSourceKind.EMPTY,
                    index = null,
                    rules = emptyList(),
                )
            }
        }
    }

    suspend fun refreshOfficialRules(): SmsCodeRuleCatalogRefreshResult {
        return runCatching {
            val indexText = remoteSource.readIndex()
            val index = decodeIndex(indexText)
            val ruleTexts = linkedMapOf<String, String>()
            for (file in index.files) {
                val text = remoteSource.readRule(file.path)
                verifyFile(file, text)
                ruleTexts[file.path] = text
            }
            cacheSource.writeCatalog(indexText, ruleTexts)
            val snapshot = buildSnapshot(
                sourceKind = SmsCodeRuleCatalogSourceKind.REMOTE,
                index = index,
                ruleTexts = ruleTexts,
            )
            SmsCodeRuleCatalogRefreshResult(success = true, snapshot = snapshot)
        }.getOrElse { throwable ->
            SmsCodeRuleCatalogRefreshResult(
                success = false,
                snapshot = loadOfficialRules(),
                errorMessage = throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private suspend fun loadFrom(
        source: SmsCodeRuleCatalogSource,
        kind: SmsCodeRuleCatalogSourceKind,
    ): Result<SmsCodeRuleCatalogSnapshot> = runCatching {
        val index = decodeIndex(source.readIndex())
        val ruleTexts = linkedMapOf<String, String>()
        for (file in index.files) {
            val text = source.readRule(file.path)
            verifyFile(file, text)
            ruleTexts[file.path] = text
        }
        buildSnapshot(kind, index, ruleTexts)
    }

    private fun buildSnapshot(
        sourceKind: SmsCodeRuleCatalogSourceKind,
        index: SmsCodeRuleCatalogIndex,
        ruleTexts: Map<String, String>,
    ): SmsCodeRuleCatalogSnapshot {
        val officialRules = mutableListOf<OfficialSmsCodeRule>()
        val rejected = mutableListOf<String>()
        for (file in index.files) {
            val text = ruleTexts[file.path] ?: continue
            val documentResult = runCatching { decodeRuleSet(text) }
            val document = documentResult.getOrNull()
            if (document == null) {
                val throwable = documentResult.exceptionOrNull()
                rejected += "${file.path}: ${throwable?.message ?: throwable?.javaClass?.simpleName ?: "decode failed"}"
                continue
            }
            for (rule in document.rules) {
                val rejectReason = validateRule(rule)
                if (rejectReason != null) {
                    rejected += "${file.path}#${rule.id}: $rejectReason"
                    continue
                }
                officialRules += OfficialSmsCodeRule(
                    setId = document.id,
                    setName = document.name,
                    setLocale = document.locale,
                    setUpdatedAt = document.updatedAt,
                    sourcePath = file.path,
                    id = rule.id,
                    company = rule.company,
                    codeKeyword = rule.codeKeyword,
                    codeRegex = rule.codeRegex,
                    senderRegex = rule.senderRegex,
                    packageNameHint = rule.packageNameHint,
                    priority = rule.priority,
                    notes = rule.notes,
                )
            }
        }
        return SmsCodeRuleCatalogSnapshot(
            sourceKind = sourceKind,
            index = index,
            rules = officialRules,
            rejectedRules = rejected,
        )
    }

    companion object {
        private val json = JsonConfig.json

        fun decodeIndex(text: String): SmsCodeRuleCatalogIndex = json.decodeFromString(text)

        fun decodeRuleSet(text: String): SmsCodeRuleSetDocument = json.decodeFromString(text)

        fun verifyFile(file: SmsCodeRuleCatalogFile, text: String) {
            require(file.size == text.toByteArray(Charsets.UTF_8).size) {
                "Size mismatch for ${file.path}"
            }
            require(file.sha256.equals(sha256(text), ignoreCase = true)) {
                "SHA-256 mismatch for ${file.path}"
            }
        }

        private fun validateRule(rule: SmsCodeRuleDocument): String? {
            if (rule.codeKeyword.isBlank()) return "blank codeKeyword"
            if (rule.codeRegex.isBlank()) return "blank codeRegex"
            return runCatching {
                Pattern.compile(rule.codeRegex)
                null
            }.getOrElse { throwable ->
                throwable.message ?: throwable.javaClass.simpleName
            }
        }

        private fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
