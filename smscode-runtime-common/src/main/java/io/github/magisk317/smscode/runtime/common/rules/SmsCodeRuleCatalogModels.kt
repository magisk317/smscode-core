package io.github.magisk317.smscode.runtime.common.rules

import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRuleSource
import kotlinx.serialization.Serializable

const val DEFAULT_SMS_CODE_RULES_REPOSITORY = "magisk317/smscode-rules"
const val DEFAULT_SMS_CODE_RULES_BRANCH = "main"
const val DEFAULT_SMS_CODE_RULES_ASSET_ROOT = "smscode-rules"
const val SMS_CODE_RULES_INDEX_PATH = "_meta/rules-index.json"
const val SMS_CODE_RULES_BUNDLED_INDEX_PATH = "meta/rules-index.json"

@Serializable
data class SmsCodeRuleRemoteSource(
    val repository: String = DEFAULT_SMS_CODE_RULES_REPOSITORY,
    val branch: String = DEFAULT_SMS_CODE_RULES_BRANCH,
) {
    val rawBaseUrl: String
        get() = "https://raw.githubusercontent.com/$repository/$branch"
}

@Serializable
data class SmsCodeRuleCatalogIndex(
    val sourceRepo: String,
    val branch: String,
    val generatedAt: String,
    val files: List<SmsCodeRuleCatalogFile>,
)

@Serializable
data class SmsCodeRuleCatalogFile(
    val path: String,
    val id: String,
    val name: String,
    val locale: String,
    val updatedAt: String,
    val ruleCount: Int,
    val sha256: String,
    val size: Int,
)

@Serializable
data class SmsCodeRuleSetDocument(
    val specVersion: Int,
    val id: String,
    val name: String,
    val description: String? = null,
    val locale: String,
    val updatedAt: String,
    val rules: List<SmsCodeRuleDocument>,
)

@Serializable
data class SmsCodeRuleDocument(
    val id: String,
    val company: String? = null,
    val codeKeyword: String,
    val codeRegex: String,
    val senderRegex: String? = null,
    val packageNameHint: String? = null,
    val priority: Int = 0,
    val notes: String? = null,
    val examples: List<SmsCodeRuleExample> = emptyList(),
) {
    fun toRuleSpec(): SmsCodeRuleSpec = SmsCodeRuleSpec(
        company = company?.takeIf { it.isNotBlank() },
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        senderRegex = senderRegex?.takeIf { it.isNotBlank() },
        packageNameHint = packageNameHint?.takeIf { it.isNotBlank() },
        priority = priority,
        source = SmsCodeMatchedRuleSource.OFFICIAL,
    )
}

@Serializable
data class SmsCodeRuleExample(
    val body: String,
    val expectedCode: String,
    val notes: String? = null,
)

data class OfficialSmsCodeRule(
    val setId: String,
    val setName: String,
    val setLocale: String,
    val setUpdatedAt: String,
    val sourcePath: String,
    val id: String,
    val company: String?,
    val codeKeyword: String,
    val codeRegex: String,
    val senderRegex: String? = null,
    val packageNameHint: String? = null,
    val priority: Int = 0,
    val notes: String? = null,
) {
    fun toRuleSpec(): SmsCodeRuleSpec = SmsCodeRuleSpec(
        company = company?.takeIf { it.isNotBlank() },
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        senderRegex = senderRegex?.takeIf { it.isNotBlank() },
        packageNameHint = packageNameHint?.takeIf { it.isNotBlank() },
        priority = priority,
        source = SmsCodeMatchedRuleSource.OFFICIAL,
    )
}

enum class SmsCodeRuleCatalogSourceKind {
    CACHE,
    BUNDLED,
    REMOTE,
    EMPTY,
}

data class SmsCodeRuleCatalogSnapshot(
    val sourceKind: SmsCodeRuleCatalogSourceKind,
    val index: SmsCodeRuleCatalogIndex?,
    val rules: List<OfficialSmsCodeRule>,
    val rejectedRules: List<String> = emptyList(),
    val loadedAtMillis: Long = System.currentTimeMillis(),
)

data class SmsCodeRuleCatalogRefreshResult(
    val success: Boolean,
    val snapshot: SmsCodeRuleCatalogSnapshot?,
    val errorMessage: String? = null,
)
