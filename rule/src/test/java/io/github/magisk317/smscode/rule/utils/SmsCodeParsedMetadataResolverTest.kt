package io.github.magisk317.smscode.rule.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmsCodeParsedMetadataResolverTest {
    @Test
    fun resolve_prefersFirstCandidateWithPackage() {
        val result = SmsCodeParsedMetadataResolver.resolve(
            body = "body",
            parseCompanyCandidates = { listOf("【Ignored】", "[Bank]") },
            parseCompany = { "Ignored Bank" },
            findPackageNameByLabel = { label ->
                if (label == "Bank") "com.bank.app" else null
            },
        )

        assertEquals("Bank", result.company)
        assertEquals("com.bank.app", result.packageName)
    }

    @Test
    fun resolve_fallsBackToParsedCompanyPackage() {
        val result = SmsCodeParsedMetadataResolver.resolve(
            body = "body",
            parseCompanyCandidates = { listOf("【Unknown】") },
            parseCompany = { "Known" },
            findPackageNameByLabel = { label ->
                if (label == "Known") "com.known.app" else null
            },
        )

        assertEquals("Known", result.company)
        assertEquals("com.known.app", result.packageName)
    }

    @Test
    fun resolve_returnsEmptyCompanyWhenNoCandidate() {
        val result = SmsCodeParsedMetadataResolver.resolve(
            body = "body",
            parseCompanyCandidates = { emptyList() },
            parseCompany = { "" },
            findPackageNameByLabel = { null },
        )

        assertEquals("", result.company)
        assertNull(result.packageName)
    }
}
