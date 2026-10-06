package io.github.magisk317.smscode.runtime.common.sms

import android.content.Context
import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.rule.utils.SmsCodeUtils
import io.github.magisk317.xposed.logging.MagiskOtel

fun interface SmsKeywordProvider {
    suspend fun getKeywordsRegex(context: Context, overrideKeywordsRegex: String?): String
}

fun interface SmsCodeRuleProvider {
    suspend fun getRuleSpecs(context: Context): List<SmsCodeRuleSpec>
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
        source: SmsCodeParseSource? = null,
    ): String {
        return parseSmsCodeResultIfExists(context, content, override, source).code
    }

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        override: String? = null,
        source: SmsCodeParseSource? = null,
    ): SmsCodeParseResult {
        return SmsCodeUtils.parseSmsCodeResultIfExists(
            content = content,
            keywordsRegex = keywordProvider.getKeywordsRegex(context, override),
            rules = ruleProvider.getRuleSpecs(context),
            source = source,
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
    fun match(context: Context, sender: String?, body: String?): BlacklistMatchResult {
        val startedAt = System.nanoTime()
        val result = SmsBlacklistUtils.match(
            config = configProvider.getConfig(context),
            sender = sender,
            body = body,
        )
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "sms.block",
            attributes = mapOf(
                "result" to if (result.matched) "ok" else "skip",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "match",
                "reason" to if (result.matched) (result.matchType ?: "matched") else "no_match",
                "action_block" to result.actionBlock.toString(),
                "action_delete" to result.actionDelete.toString(),
            ),
            statusOk = true,
        )
        // Hand back the shared verification type so hosts do not each redefine it.
        return BlacklistMatchResult(
            matched = result.matched,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
        )
    }
}
