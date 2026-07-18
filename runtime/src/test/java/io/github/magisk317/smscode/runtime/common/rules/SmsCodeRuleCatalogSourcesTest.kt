package io.github.magisk317.smscode.runtime.common.rules

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SmsCodeRuleCatalogSourcesTest {
    @Test
    fun `default remote source prefers github then falls back to gitlab`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val source = RemoteSmsCodeRuleCatalogSource { url ->
            requestedUrls += url
            if (url.startsWith("https://raw.githubusercontent.com/")) {
                throw IOException("not found")
            }
            when {
                url.endsWith(SMS_CODE_RULES_INDEX_PATH) -> "index"
                else -> "rule"
            }
        }

        assertEquals("index", source.readIndex())
        assertEquals("rule", source.readRule("rules/acme rule.json"))
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/magisk317/smscode-rules/main/_meta/rules-index.json",
                "https://gitlab.com/magisk3171/magisk-smscode-rules/-/raw/main/_meta/rules-index.json",
                "https://gitlab.com/magisk3171/magisk-smscode-rules/-/raw/main/rules/acme%20rule.json",
            ),
            requestedUrls,
        )
    }

    @Test
    fun `successful github index keeps rule files on github`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val source = RemoteSmsCodeRuleCatalogSource { url ->
            requestedUrls += url
            if (url.endsWith(SMS_CODE_RULES_INDEX_PATH)) "index" else "rule"
        }

        source.readIndex()
        source.readRule("rules/acme.json")

        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/magisk317/smscode-rules/main/_meta/rules-index.json",
                "https://raw.githubusercontent.com/magisk317/smscode-rules/main/rules/acme.json",
            ),
            requestedUrls,
        )
    }

    @Test
    fun `custom full index URL overrides default sources`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val remote = SmsCodeRuleRemoteSource(
            customBaseUrl = " https://rules.example.test/catalog/_meta/rules-index.json/ ",
        )
        val source = RemoteSmsCodeRuleCatalogSource(remoteSource = remote) { url ->
            requestedUrls += url
            "ok"
        }

        source.readIndex()
        source.readRule("rules/acme.json")

        assertEquals(
            listOf(
                "https://rules.example.test/catalog/_meta/rules-index.json",
                "https://rules.example.test/catalog/rules/acme.json",
            ),
            requestedUrls,
        )
    }

    @Test
    fun `custom source rejects unsupported schemes`() {
        assertThrows(IllegalArgumentException::class.java) {
            SmsCodeRuleRemoteSource.normalizeCustomBaseUrl("ftp://rules.example.test/catalog")
        }
    }

    @Test
    fun `remote source propagates cancellation without trying fallback`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val source = RemoteSmsCodeRuleCatalogSource { url ->
            requestedUrls += url
            throw CancellationException("cancelled")
        }

        val failure = runCatching { source.readIndex() }.exceptionOrNull()

        assertEquals(CancellationException::class.java, failure?.javaClass)
        assertEquals(1, requestedUrls.size)
    }
}
