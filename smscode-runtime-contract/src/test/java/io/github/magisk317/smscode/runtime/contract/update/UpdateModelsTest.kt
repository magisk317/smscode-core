package io.github.magisk317.smscode.runtime.contract.update

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateModelsTest {

    @Test
    fun `upgrade info serializes protocol payload`() {
        val info = UpgradeInfo(
            versionCode = 22,
            versionName = "0.1.2",
            htmlUrl = "https://example.invalid/release",
            changelog = "hello",
            versionLogs = listOf(VersionLog(name = "0.1.2", code = 22, desc = "notes")),
            apks = listOf(UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "https://example.invalid/app.apk")),
            signingCertSha256 = "AB:CD",
        )

        val json = Json.encodeToString(UpgradeInfo.serializer(), info)
        val restored = Json.decodeFromString(UpgradeInfo.serializer(), json)

        assertEquals(info, restored)
    }
}
