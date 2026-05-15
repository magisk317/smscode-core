package io.github.magisk317.smscode.runtime.common.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class RuleImporterExtendedFieldsTest {

    @Test
    fun `parsePayload reads optional protocol rule fields`() {
        val payload = """
            {
              "version": 2,
              "schema_version": 2,
              "app_version": "0.1.2",
              "rules": [
                {
                  "company": "ACME",
                  "code_keyword": "otp",
                  "code_regex": "\\d{6}",
                  "sender_regex": "ACME.*",
                  "package_name_hint": "com.acme.app",
                  "priority": 9
                }
              ]
            }
        """.trimIndent()

        val result = RuleImporter(ByteArrayInputStream(payload.toByteArray())).parsePayload()
        val rule = result.rules.single()

        assertEquals("ACME", rule.company)
        assertEquals("otp", rule.codeKeyword)
        assertEquals("\\d{6}", rule.codeRegex)
        assertEquals("ACME.*", rule.senderRegex)
        assertEquals("com.acme.app", rule.packageNameHint)
        assertEquals(9, rule.priority)
    }
}
