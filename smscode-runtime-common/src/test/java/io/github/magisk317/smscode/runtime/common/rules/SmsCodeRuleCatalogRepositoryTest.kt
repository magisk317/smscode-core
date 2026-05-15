package io.github.magisk317.smscode.runtime.common.rules

import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.domain.utils.SmsCodeUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SmsCodeRuleCatalogRepositoryTest {
    @Test
    fun `decode index and rule set map to official specs`() {
        val index = SmsCodeRuleCatalogRepository.decodeIndex(indexJson(ruleText()))
        val ruleSet = SmsCodeRuleCatalogRepository.decodeRuleSet(ruleText())

        assertEquals("magisk317/smscode-rules", index.sourceRepo)
        assertEquals("cn.test.acme", ruleSet.id)
        assertEquals("Acme", ruleSet.rules.first().company)
    }

    @Test
    fun `loadOfficialRules falls back from empty cache to bundled`() = runBlocking {
        val repository = SmsCodeRuleCatalogRepository(
            cacheSource = MemorySource(),
            bundledSource = MemorySource(indexJson(ruleText()), mutableMapOf("rules/acme.json" to ruleText())),
            remoteSource = FailingSource,
        )

        val snapshot = repository.loadOfficialRules()

        assertEquals(SmsCodeRuleCatalogSourceKind.BUNDLED, snapshot.sourceKind)
        assertEquals(1, snapshot.rules.size)
        assertEquals("otp", snapshot.rules.first().id)
    }

    @Test
    fun `refresh writes remote catalog into cache`() = runBlocking {
        val cache = MemorySource()
        val repository = SmsCodeRuleCatalogRepository(
            cacheSource = cache,
            bundledSource = FailingSource,
            remoteSource = MemorySource(indexJson(ruleText()), mutableMapOf("rules/acme.json" to ruleText())),
        )

        val result = repository.refreshOfficialRules()
        val cached = repository.loadOfficialRules()

        assertTrue(result.success)
        assertEquals(SmsCodeRuleCatalogSourceKind.REMOTE, result.snapshot?.sourceKind)
        assertEquals(SmsCodeRuleCatalogSourceKind.CACHE, cached.sourceKind)
        assertEquals(1, cached.rules.size)
    }

    @Test
    fun `merger keeps user rules first and sorts official rules by priority`() = runBlocking {
        val userRule = SmsCodeRuleSpec(company = "Acme", codeKeyword = "登录", codeRegex = "(?<=登录码:)\\d{4}")
        val officialLow = officialRule(id = "low", codeKeyword = "验证码", codeRegex = "(?<=验证码:)\\d{6}", priority = 1)
        val officialHigh = officialRule(id = "high", codeKeyword = "验证码", codeRegex = "(?<=验证码:)\\d{5}", priority = 100)

        val merged = SmsCodeRuleMerger.merge(
            userRules = listOf(userRule),
            officialRules = listOf(officialLow, officialHigh),
        )
        val userResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】登录码:4321 验证码:12345",
            keywordsRegex = "验证码|登录码",
            rules = merged,
        )
        val officialResult = SmsCodeUtils.parseSmsCodeResultIfExists(
            content = "【Acme】验证码:12345",
            keywordsRegex = "验证码",
            rules = merged,
        )

        assertEquals("4321", userResult.code)
        assertEquals(SmsCodeMatchedRuleSource.CUSTOM, userResult.matchedRule?.source)
        assertEquals("12345", officialResult.code)
        assertEquals(SmsCodeMatchedRuleSource.OFFICIAL, officialResult.matchedRule?.source)
    }

    @Test
    fun `refresh failure returns bundled snapshot`() = runBlocking {
        val repository = SmsCodeRuleCatalogRepository(
            cacheSource = MemorySource(),
            bundledSource = MemorySource(indexJson(ruleText()), mutableMapOf("rules/acme.json" to ruleText())),
            remoteSource = FailingSource,
        )

        val result = repository.refreshOfficialRules()

        assertFalse(result.success)
        assertEquals(SmsCodeRuleCatalogSourceKind.BUNDLED, result.snapshot?.sourceKind)
    }

    private fun officialRule(
        id: String,
        codeKeyword: String,
        codeRegex: String,
        priority: Int,
    ): OfficialSmsCodeRule = OfficialSmsCodeRule(
        setId = "set",
        setName = "Set",
        setLocale = "zh-CN",
        setUpdatedAt = "2026-05-01T00:00:00+08:00",
        sourcePath = "rules/set.json",
        id = id,
        company = "Acme",
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        priority = priority,
    )

    private fun ruleText(): String = """
        {
          "specVersion": 1,
          "id": "cn.test.acme",
          "name": "Acme",
          "locale": "zh-CN",
          "updatedAt": "2026-05-01T00:00:00+08:00",
          "rules": [
            {
              "id": "otp",
              "company": "Acme",
              "codeKeyword": "验证码",
              "codeRegex": "(?<=验证码:)\\d{6}",
              "priority": 50,
              "examples": [
                {
                  "body": "【Acme】验证码:123456",
                  "expectedCode": "123456"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun indexJson(ruleText: String): String = """
        {
          "sourceRepo": "magisk317/smscode-rules",
          "branch": "main",
          "generatedAt": "2026-05-10T00:00:00Z",
          "files": [
            {
              "path": "rules/acme.json",
              "id": "cn.test.acme",
              "name": "Acme",
              "locale": "zh-CN",
              "updatedAt": "2026-05-01T00:00:00+08:00",
              "ruleCount": 1,
              "sha256": "${sha256(ruleText)}",
              "size": ${ruleText.toByteArray(Charsets.UTF_8).size}
            }
          ]
        }
    """.trimIndent()

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class MemorySource(
        private var index: String? = null,
        private val rules: MutableMap<String, String> = linkedMapOf(),
    ) : WritableSmsCodeRuleCatalogSource {
        override suspend fun readIndex(): String = index ?: error("missing index")

        override suspend fun readRule(path: String): String = rules[path] ?: error("missing rule: $path")

        override suspend fun writeCatalog(indexText: String, ruleTexts: Map<String, String>) {
            index = indexText
            rules.clear()
            rules.putAll(ruleTexts)
        }
    }

    private object FailingSource : WritableSmsCodeRuleCatalogSource {
        override suspend fun readIndex(): String = error("unavailable")
        override suspend fun readRule(path: String): String = error("unavailable")
        override suspend fun writeCatalog(indexText: String, ruleTexts: Map<String, String>) = error("unavailable")
    }
}
