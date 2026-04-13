package io.github.magisk317.smscode.runtime.common.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateArtifactConfigTest {

    @Test
    fun artifactConfig_preservesSmsCodePrefix() {
        assertEquals("XposedSmsCode", UpdateArtifactConfig(apkFilePrefix = "XposedSmsCode").apkFilePrefix)
    }

    @Test
    fun artifactConfig_preservesRelayPrefix() {
        assertEquals("XinyiRelay", UpdateArtifactConfig(apkFilePrefix = "XinyiRelay").apkFilePrefix)
    }

    @Test
    fun buildTargetFileName_usesConfiguredPrefixAndNormalizedAbi() {
        val fileName = UpgradeDownloader.buildTargetFileName(
            versionCode = 321,
            asset = UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "https://example.com"),
            artifactConfig = UpdateArtifactConfig(apkFilePrefix = "XposedSmsCode"),
        )
        assertEquals("XposedSmsCode-v321-arm64-v8a.apk", fileName)
    }

    @Test
    fun buildTargetFileName_fallsBackToUniversalAbi() {
        val fileName = UpgradeDownloader.buildTargetFileName(
            versionCode = 321,
            asset = UpgradeApkAsset(abi = "", downloadUrl = "https://example.com"),
            artifactConfig = UpdateArtifactConfig(apkFilePrefix = "XinyiRelay"),
        )
        assertEquals("XinyiRelay-v321-universal.apk", fileName)
    }
}
