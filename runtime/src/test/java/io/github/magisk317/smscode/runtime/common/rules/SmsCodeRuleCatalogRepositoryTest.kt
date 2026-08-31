package io.github.magisk317.smscode.runtime.common.rules

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
    fun `refresh retries the entire catalog on the fallback source`() = runBlocking {
        val ruleText = ruleText()
        val indexText = indexJson(ruleText)
        val cache = MemorySource()
        val primary = object : SmsCodeRuleCatalogSource {
            override suspend fun readIndex(): String = indexText

            override suspend fun readRule(path: String): String {
                throw IllegalStateException("primary rule unavailable")
            }
        }
        val repository = SmsCodeRuleCatalogRepository(
            cacheSource = cache,
            bundledSource = FailingSource,
            remoteSource = primary,
            remoteFallbackSources = listOf(
                MemorySource(indexText, mutableMapOf("rules/acme.json" to ruleText)),
            ),
        )

        val result = repository.refreshOfficialRules()

        assertTrue(result.success)
        assertEquals(1, result.snapshot?.rules?.size)
        assertEquals(SmsCodeRuleCatalogSourceKind.CACHE, repository.loadOfficialRules().sourceKind)
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
