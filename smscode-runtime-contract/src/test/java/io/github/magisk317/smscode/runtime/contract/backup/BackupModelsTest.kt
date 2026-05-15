package io.github.magisk317.smscode.runtime.contract.backup

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupModelsTest {

    @Test
    fun `backup payload serializes extended rule fields`() {
        val payload = BackupPayload(
            appVersion = "0.1.2",
            rules = listOf(
                BackupRule(
                    company = "ACME",
                    codeKeyword = "otp",
                    codeRegex = "\\d{6}",
                    senderRegex = "ACME.*",
                    packageNameHint = "com.acme.app",
                    priority = 9,
                ),
            ),
        )

        val json = Json.encodeToString(BackupPayload.serializer(), payload)
        val restored = Json.decodeFromString(BackupPayload.serializer(), json)

        assertEquals(payload, restored)
    }
}
