package io.github.magisk317.smscode.runtime.common.rules

import io.github.magisk317.smscode.rule.catalog.OfficialSmsCodeRule
import io.github.magisk317.smscode.rule.catalog.SmsCodeRuleCatalogIndex
import java.net.URI
import kotlinx.serialization.Serializable

const val DEFAULT_SMS_CODE_RULES_REPOSITORY = "magisk3171/smscode-rules"
const val DEFAULT_SMS_CODE_RULES_BRANCH = "main"
const val DEFAULT_SMS_CODE_RULES_GITLAB_RAW_BASE_URL =
    "https://gitlab.com/magisk3171/magisk-smscode-rules/-/raw/main"
const val DEFAULT_SMS_CODE_RULES_ASSET_ROOT = "smscode-rules"
const val SMS_CODE_RULES_INDEX_PATH = "_meta/rules-index.json"
const val SMS_CODE_RULES_BUNDLED_INDEX_PATH = "meta/rules-index.json"

@Serializable
data class SmsCodeRuleRemoteSource(
    val repository: String = DEFAULT_SMS_CODE_RULES_REPOSITORY,
    val branch: String = DEFAULT_SMS_CODE_RULES_BRANCH,
    val customBaseUrl: String? = null,
) {
    val rawBaseUrl: String
        get() = rawBaseUrls.first()

    val rawBaseUrls: List<String>
        get() {
            normalizeCustomBaseUrl(customBaseUrl.orEmpty())?.let { return listOf(it) }
            return buildList {
                add("https://raw.githubusercontent.com/$repository/$branch")
                if (
                    repository == DEFAULT_SMS_CODE_RULES_REPOSITORY &&
                    branch == DEFAULT_SMS_CODE_RULES_BRANCH
                ) {
                    add(DEFAULT_SMS_CODE_RULES_GITLAB_RAW_BASE_URL)
                }
            }
        }

    companion object {
        fun normalizeCustomBaseUrl(value: String): String? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null

            val uri = runCatching { URI(trimmed) }
                .getOrElse { throw IllegalArgumentException("Invalid rule source URL", it) }
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                "Rule source URL must use HTTP or HTTPS"
            }
            require(!uri.host.isNullOrBlank()) { "Rule source URL must include a host" }
            require(uri.rawQuery == null && uri.rawFragment == null) {
                "Rule source URL must not include a query or fragment"
            }

            val indexSuffix = "/$SMS_CODE_RULES_INDEX_PATH"
            val normalizedPath = uri.path.orEmpty().trimEnd('/').let { path ->
                if (path.endsWith(indexSuffix)) path.removeSuffix(indexSuffix) else path
            }
            return URI(
                uri.scheme.lowercase(),
                uri.userInfo,
                uri.host,
                uri.port,
                normalizedPath.ifEmpty { null },
                null,
                null,
            ).toASCIIString().trimEnd('/')
        }
    }
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
