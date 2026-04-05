package io.github.magisk317.smscode.runtime.common.sms

import android.content.Context
import io.github.magisk317.smscode.domain.model.SmsBlacklistConfig
import io.github.magisk317.smscode.domain.model.SmsBlacklistMatchResult
import io.github.magisk317.smscode.domain.model.SmsCodeParseResult
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.domain.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.domain.utils.SmsCodeUtils

fun interface SmsKeywordProvider {
    suspend fun getKeywordsRegex(context: Context, overrideKeywordsRegex: String?): String
}

fun interface SmsCodeRuleProvider {
    fun getRuleSpecs(context: Context): List<SmsCodeRuleSpec>
}

fun interface SmsPackageLabelResolver {
    fun resolvePackageName(context: Context, label: String): String?
}

fun interface SmsBlacklistConfigProvider {
    fun getConfig(context: Context): SmsBlacklistConfig
}

class RuntimeSmsCodeAdapter(
    private val keywordProvider: SmsKeywordProvider,
    private val ruleProvider: SmsCodeRuleProvider,
    private val labelResolver: SmsPackageLabelResolver,
) {

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        override: String? = null,
    ): String {
        return parseSmsCodeResultIfExists(context, content, override).code
    }

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        override: String? = null,
    ): SmsCodeParseResult {
        return SmsCodeUtils.parseSmsCodeResultIfExists(
            content = content,
            keywordsRegex = keywordProvider.getKeywordsRegex(context, override),
            rules = ruleProvider.getRuleSpecs(context),
        )
    }

    fun parseCompany(content: String): String = SmsCodeUtils.parseCompany(content)

    fun parseCompanyCandidates(content: String): List<String> = SmsCodeUtils.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return SmsCodeUtils.findPackageNameByLabel(
            label = label,
            resolver = { target -> labelResolver.resolvePackageName(context, target) },
        )
    }
}

class RuntimeSmsBlacklistAdapter(
    private val configProvider: SmsBlacklistConfigProvider,
) {
    fun match(context: Context, sender: String?, body: String?): SmsBlacklistMatchResult {
        return SmsBlacklistUtils.match(
            config = configProvider.getConfig(context),
            sender = sender,
            body = body,
        )
    }
}
