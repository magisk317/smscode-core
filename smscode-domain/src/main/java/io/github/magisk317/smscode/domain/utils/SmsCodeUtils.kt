package io.github.magisk317.smscode.domain.utils

import io.github.magisk317.smscode.domain.model.AppLabelResolver
import io.github.magisk317.smscode.domain.model.BuiltinSmsCodeRules
import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRule
import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.domain.model.SmsCodeParseResult
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import java.util.Locale
import java.util.regex.Pattern

object SmsCodeUtils {
    private data class ParseCandidate(
        val code: String,
        val matchedRule: SmsCodeMatchedRule? = null,
    )

    private const val LEVEL_DIGITAL_6 = 4
    private const val LEVEL_DIGITAL_4 = 3
    private const val LEVEL_DIGITAL_OTHERS = 2
    private const val LEVEL_TEXT = 1
    private const val LEVEL_CHARACTER = 0
    private const val LEVEL_NONE = -1
    private const val KEYWORD_DISTANCE_THRESHOLD = 30
    private val urlSchemeTokens = setOf("http", "https", "www")
    private val dateTimeUnitTokens = setOf('年', '月', '日', '号', '时', '點', '点', '分', '秒')

    suspend fun parseSmsCodeIfExists(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
    ): String = parseSmsCodeResultIfExists(content, keywordsRegex, rules).code

    suspend fun parseSmsCodeResultIfExists(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
    ): SmsCodeParseResult {
        val customResult = parseByCustomRules(content, rules)
        val defaultResult = parseByDefaultRule(content, keywordsRegex)
        val bestResult = pickBetterCandidate(customResult, defaultResult)
        return SmsCodeParseResult(
            code = bestResult.code,
            matchedRule = bestResult.matchedRule,
        )
    }

    @JvmStatic
    fun parseCompany(content: String): String {
        val possibleCompanies = parseCompanyCandidates(content)
        if (possibleCompanies.isEmpty()) return ""
        return possibleCompanies.joinToString(" ")
    }

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> {
        val regex = "((?<=【)(.*?)(?=】))|((?<=\\[)(.*?)(?=\\]))"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        val possibleCompanies = mutableListOf<String>()
        while (matcher.find()) {
            possibleCompanies.add(matcher.group())
        }
        return possibleCompanies
    }

    @JvmStatic
    fun findPackageNameByLabel(label: String?, resolver: AppLabelResolver): String? {
        if (label.isNullOrBlank()) return null
        return resolver.findPackageNameByLabel(label)
    }

    private fun containsChinese(text: String): Boolean {
        val regex = "[\u4e00-\u9fa5]|。"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    private fun parseKeyword(keywordsRegex: String, content: String): String {
        if (keywordsRegex.isBlank()) return ""
        val pattern = Pattern.compile(keywordsRegex)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group() else ""
    }

    private fun parseByDefaultRule(content: String, keywordsRegex: String): ParseCandidate {
        val keyword = parseKeyword(keywordsRegex, content)
        if (keyword.isEmpty()) return ParseCandidate(code = "")
        val cnCode = if (containsChinese(content)) getSmsCodeCN(keyword, content) else ""
        val enCode = getSmsCodeEN(keyword, content)
        return pickBetterCandidate(
            first = ParseCandidate(
                code = cnCode,
                matchedRule = cnCode.takeIf { it.isNotEmpty() }?.let {
                    SmsCodeMatchedRule(
                        source = SmsCodeMatchedRuleSource.BUILTIN,
                        ordinal = 1,
                    )
                },
            ),
            second = ParseCandidate(
                code = enCode,
                matchedRule = enCode.takeIf { it.isNotEmpty() }?.let {
                    SmsCodeMatchedRule(
                        source = SmsCodeMatchedRuleSource.BUILTIN,
                        ordinal = 2,
                    )
                },
            ),
        )
    }

    private fun getSmsCodeCN(keyword: String, content: String): String {
        val codeRegex = BuiltinSmsCodeRules.DEFAULT_ALPHANUMERIC_CODE_REGEX
        val handledContent = removeAllWhiteSpaces(content)
        var smsCode = getSmsCode(codeRegex, keyword, handledContent)
        if (smsCode.isEmpty()) {
            smsCode = getSmsCode(codeRegex, keyword, content)
        }
        return smsCode
    }

    private fun getSmsCodeEN(keyword: String, content: String): String {
        val codeRegex = BuiltinSmsCodeRules.DEFAULT_DIGITS_CODE_REGEX
        var smsCode = getSmsCode(codeRegex, keyword, content)
        if (smsCode.isEmpty()) {
            val handledContent = removeAllWhiteSpaces(content)
            smsCode = getSmsCode(codeRegex, keyword, handledContent)
        }
        return smsCode
    }

    private fun removeAllWhiteSpaces(content: String): String = content.replace("\\s*".toRegex(), "")

    private fun getSmsCode(codeRegex: String, keyword: String, content: String): String {
        val matcher = Pattern.compile(codeRegex).matcher(content)
        val possibleCodes = mutableListOf<String>()
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.lowercase(Locale.ROOT) in urlSchemeTokens) continue
            if (isLikelyDateTimeToken(candidate, content)) continue
            possibleCodes.add(candidate)
        }
        if (possibleCodes.isEmpty()) return ""

        var filteredCodes = possibleCodes.filter { isNearToKeyword(keyword, it, content) }
        if (filteredCodes.isEmpty()) {
            filteredCodes = possibleCodes
        }

        var maxMatchLevel = LEVEL_NONE
        var minDistance = content.length
        var smsCode = ""
        for (filteredCode in filteredCodes) {
            val curLevel = getMatchLevel(filteredCode)
            if (curLevel > maxMatchLevel) {
                maxMatchLevel = curLevel
                minDistance = distanceToKeyword(keyword, filteredCode, content)
                smsCode = filteredCode
            } else if (curLevel == maxMatchLevel) {
                val curDistance = distanceToKeyword(keyword, filteredCode, content)
                if (curDistance < minDistance) {
                    minDistance = curDistance
                    smsCode = filteredCode
                }
            }
        }
        return smsCode
    }

    private fun isLikelyDateTimeToken(candidate: String, content: String): Boolean {
        if (!candidate.all(Char::isDigit)) return false
        val candidateIdx = content.indexOf(candidate)
        if (candidateIdx < 0) return false
        val prevChar = content.getOrNull(candidateIdx - 1)
        val nextChar = content.getOrNull(candidateIdx + candidate.length)
        return prevChar in dateTimeUnitTokens || nextChar in dateTimeUnitTokens
    }

    private fun getMatchLevel(matchedStr: String): Int = when {
        matchedStr.matches("^[0-9]{6}$".toRegex()) -> LEVEL_DIGITAL_6
        matchedStr.matches("^[0-9]{4}$".toRegex()) -> LEVEL_DIGITAL_4
        matchedStr.matches("^[0-9]*$".toRegex()) -> LEVEL_DIGITAL_OTHERS
        matchedStr.matches("^[a-zA-Z]*$".toRegex()) -> LEVEL_CHARACTER
        else -> LEVEL_TEXT
    }

    private fun pickBetterCandidate(first: ParseCandidate, second: ParseCandidate): ParseCandidate {
        if (first.code.isEmpty()) return second
        if (second.code.isEmpty()) return first
        val firstLevel = getMatchLevel(first.code)
        val secondLevel = getMatchLevel(second.code)
        return if (secondLevel > firstLevel) second else first
    }

    private fun isNearToKeyword(keyword: String, possibleCode: String, content: String): Boolean =
        distanceToKeyword(keyword, possibleCode, content) <= KEYWORD_DISTANCE_THRESHOLD

    private fun distanceToKeyword(keyword: String, possibleCode: String, content: String): Int {
        val possibleCodeIdx = content.indexOf(possibleCode)
        if (possibleCodeIdx < 0) return content.length

        var minDistance = content.length
        var searchStart = 0
        while (searchStart < content.length) {
            val keywordIdx = content.indexOf(keyword, startIndex = searchStart)
            if (keywordIdx < 0) break
            val distance = kotlin.math.abs(keywordIdx - possibleCodeIdx)
            if (distance < minDistance) {
                minDistance = distance
            }
            searchStart = keywordIdx + keyword.length
        }
        return minDistance
    }

    private fun parseByCustomRules(content: String, rules: List<SmsCodeRuleSpec>): ParseCandidate {
        val lowerContent = content.lowercase()
        for ((index, rule) in rules.withIndex()) {
            if (lowerContent.contains(rule.company?.lowercase() ?: "") &&
                lowerContent.contains(rule.codeKeyword.lowercase())
            ) {
                val matcher = Pattern.compile(rule.codeRegex).matcher(content)
                if (matcher.find()) {
                    return ParseCandidate(
                        code = matcher.group(),
                        matchedRule = SmsCodeMatchedRule(
                            source = SmsCodeMatchedRuleSource.CUSTOM,
                            ordinal = index + 1,
                        ),
                    )
                }
            }
        }
        return ParseCandidate(code = "")
    }
}
