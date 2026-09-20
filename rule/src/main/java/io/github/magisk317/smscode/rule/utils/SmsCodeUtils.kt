package io.github.magisk317.smscode.rule.utils

import io.github.magisk317.smscode.rule.model.AppLabelResolver
import io.github.magisk317.smscode.rule.model.BuiltinSmsCodeRules
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRule
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
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
    private val urlContextTokens = setOf('/', '?', '&', '=', '#', '%')
    private val dateTimeUnitTokens = setOf('年', '月', '日', '号', '时', '點', '点', '分', '秒')

    suspend fun parseSmsCodeIfExists(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
        source: SmsCodeParseSource? = null,
    ): String = parseSmsCodeResultIfExists(content, keywordsRegex, rules, source).code

    suspend fun parseSmsCodeResultIfExists(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
        source: SmsCodeParseSource? = null,
    ): SmsCodeParseResult = parseSmsCodeResult(
        content = content,
        keywordsRegex = keywordsRegex,
        rules = rules,
        source = source,
    )

    fun parseSmsCode(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
        source: SmsCodeParseSource? = null,
    ): String = parseSmsCodeResult(content, keywordsRegex, rules, source).code

    fun parseSmsCodeResult(
        content: String,
        keywordsRegex: String,
        rules: List<SmsCodeRuleSpec> = emptyList(),
        source: SmsCodeParseSource? = null,
    ): SmsCodeParseResult {
        val normalizedContent = normalizeDigits(content)
        val customResult = parseByCustomRules(normalizedContent, rules, source)
        val defaultResult = parseByDefaultRule(normalizedContent, keywordsRegex)
        val bestResult = pickBetterCandidate(customResult, defaultResult)
        return SmsCodeParseResult(
            code = bestResult.code,
            matchedRule = bestResult.matchedRule,
        )
    }

    private fun normalizeDigits(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                // Eastern Arabic-Indic digits (Persian/Urdu: ۰ ۱ ۲ ۳ ۴ ۵ ۶ ۷ ۸ ۹)
                in '\u06F0'..'\u06F9' -> sb.append((ch - '\u06F0' + '0'.code).toChar())
                // Arabic-Indic digits (Arabic: ٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩)
                in '\u0660'..'\u0669' -> sb.append((ch - '\u0660' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
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
        val regex = "[一-龥]|。"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    private fun isNearToKeyword(keyword: String, match: MatchResult, content: String): Boolean {
        if (keyword.isEmpty()) return true

        var minDistance = Int.MAX_VALUE
        var keywordIndex = content.indexOf(keyword)
        while (keywordIndex != -1) {
            val distance = Math.abs(match.range.first - keywordIndex)
            if (distance < minDistance) {
                minDistance = distance
            }
            keywordIndex = content.indexOf(keyword, keywordIndex + 1)
        }

        if (minDistance == Int.MAX_VALUE) return false
        return minDistance < KEYWORD_DISTANCE_THRESHOLD
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
        val codeRegex = "(?<![a-zA-Z0-9])[0-9]{4,8}(?![a-zA-Z0-9])"
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
            if (isLikelyRejectedCodeToken(candidate, content, matcher.start())) continue
            possibleCodes.add(candidate)
        }
        if (possibleCodes.isEmpty()) return ""

        var filteredCodes = possibleCodes.filter { isNearToKeyword(keyword, it, content) }
        if (filteredCodes.isEmpty()) {
            if (keyword.isEmpty()) {
                filteredCodes = possibleCodes
            } else {
                return ""
            }
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

    private fun isLikelyDateTimeToken(
        candidate: String,
        content: String,
        candidateIndex: Int,
    ): Boolean {
        if (!candidate.all(Char::isDigit)) return false
        val candidateIdx = candidateIndex
        if (candidateIdx < 0) return false
        val prevChar = content.getOrNull(candidateIdx - 1)
        val nextChar = content.getOrNull(candidateIdx + candidate.length)
        return prevChar in dateTimeUnitTokens || nextChar in dateTimeUnitTokens
    }

    private fun isLikelyUrlToken(
        candidate: String,
        content: String,
        candidateIndex: Int,
    ): Boolean {
        val candidateIdx = candidateIndex
        if (candidateIdx < 0) return false
        val prevChar = content.getOrNull(candidateIdx - 1)
        val nextChar = content.getOrNull(candidateIdx + candidate.length)
        val contextStart = (candidateIdx - 8).coerceAtLeast(0)
        val contextEnd = (candidateIdx + candidate.length + 8).coerceAtMost(content.length)
        val nearby = content.substring(contextStart, contextEnd).lowercase(Locale.ROOT)
        if (nearby.contains("http://") || nearby.contains("https://") || nearby.contains("www.")) {
            return true
        }
        if (prevChar == ':' || nextChar == ':') {
            return nearby.contains("://")
        }
        val dotBeforeLooksLikeUrl =
            prevChar == '.' && content.getOrNull(candidateIdx - 2)?.isLetterOrDigit() == true
        val dotAfterLooksLikeUrl =
            nextChar == '.' && content.getOrNull(candidateIdx + candidate.length + 1)?.isLetterOrDigit() == true
        return prevChar in urlContextTokens ||
            nextChar in urlContextTokens ||
            dotBeforeLooksLikeUrl ||
            dotAfterLooksLikeUrl
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

    private fun parseByCustomRules(
        content: String,
        rules: List<SmsCodeRuleSpec>,
        source: SmsCodeParseSource?,
    ): ParseCandidate {
        val lowerContent = content.lowercase()
        var fallbackCandidate: ParseCandidate? = null
        for ((index, rule) in rules.withIndex()) {
            if (!matchesSource(rule, source)) continue
            if (!lowerContent.contains(rule.codeKeyword.lowercase())) continue
            val matcher = runCatching {
                Pattern.compile(rule.codeRegex).matcher(content)
            }.getOrNull() ?: continue
            val candidateCode = firstAcceptableCustomCandidate(matcher, rule.codeKeyword, content) ?: continue

            val candidate = ParseCandidate(
                code = candidateCode,
                matchedRule = SmsCodeMatchedRule(
                    source = rule.source,
                    ordinal = index + 1,
                ),
            )
            val company = rule.company?.trim().orEmpty()
            val companyMatched = company.isBlank() || lowerContent.contains(company.lowercase())
            if (companyMatched) return candidate
            if (fallbackCandidate == null) {
                fallbackCandidate = candidate
            }
        }
        return fallbackCandidate ?: ParseCandidate(code = "")
    }

    private fun firstAcceptableCustomCandidate(
        matcher: java.util.regex.Matcher,
        keyword: String,
        content: String,
    ): String? {
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.isBlank()) continue
            if (isLikelyRejectedCodeToken(candidate, content, matcher.start())) continue
            if (!isNearToKeyword(keyword, matcher.start(), content)) continue
            return candidate
        }
        return null
    }

    private fun isLikelyRejectedCodeToken(
        candidate: String,
        content: String,
        candidateIndex: Int,
    ): Boolean {
        if (candidate.none(Char::isDigit)) return true
        if (candidate.lowercase(Locale.ROOT) in urlSchemeTokens) return true
        return isLikelyDateTimeToken(candidate, content, candidateIndex) ||
            isLikelyUrlToken(candidate, content, candidateIndex) ||
            isLikelyDecimalAmountToken(candidate, content, candidateIndex)
    }

    private fun isLikelyDecimalAmountToken(
        candidate: String,
        content: String,
        candidateIndex: Int,
    ): Boolean {
        if (!candidate.all(Char::isDigit)) return false
        val candidateIdx = candidateIndex
        if (candidateIdx < 0) return false
        val prevChar = content.getOrNull(candidateIdx - 1)
        val nextChar = content.getOrNull(candidateIdx + candidate.length)
        if (prevChar != '.' && nextChar != '.') return false
        val beforeDecimal = prevChar == '.' && content.getOrNull(candidateIdx - 2)?.isDigit() == true
        val afterDecimal = nextChar == '.' && content.getOrNull(candidateIdx + candidate.length + 1)?.isDigit() == true
        return beforeDecimal || afterDecimal
    }

    private fun isNearToKeyword(keyword: String, candidateIndex: Int, content: String): Boolean {
        if (keyword.isBlank()) return true
        val lowerContent = content.lowercase()
        val lowerKeyword = keyword.lowercase()
        var minDistance = content.length
        var searchStart = 0
        while (searchStart < lowerContent.length) {
            val keywordIdx = lowerContent.indexOf(lowerKeyword, startIndex = searchStart)
            if (keywordIdx < 0) break
            val distance = kotlin.math.abs(keywordIdx - candidateIndex)
            if (distance < minDistance) {
                minDistance = distance
            }
            searchStart = keywordIdx + lowerKeyword.length
        }
        return minDistance <= KEYWORD_DISTANCE_THRESHOLD
    }

    private fun matchesSource(rule: SmsCodeRuleSpec, source: SmsCodeParseSource?): Boolean {
        if (source == null) return true
        val packageNameHint = rule.packageNameHint?.trim().orEmpty()
        if (packageNameHint.isNotEmpty()) {
            val sourcePackage = source.packageName?.trim().orEmpty()
            if (!sourcePackage.equals(packageNameHint, ignoreCase = true)) return false
        }

        val senderRegex = rule.senderRegex?.trim().orEmpty()
        if (senderRegex.isEmpty()) return true
        val pattern = runCatching { Pattern.compile(senderRegex, Pattern.CASE_INSENSITIVE) }.getOrNull()
            ?: return false
        val sourceTexts = listOfNotNull(
            source.sender?.trim()?.takeIf { it.isNotEmpty() },
            source.title?.trim()?.takeIf { it.isNotEmpty() },
            source.channelId?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (sourceTexts.isEmpty()) return false
        return sourceTexts.any { pattern.matcher(it).find() }
    }
}
