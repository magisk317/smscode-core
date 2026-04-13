package io.github.magisk317.smscode.runtime.common.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GithubUpdateCheckerTest {

    private val relayConfig = GithubUpdateConfig(
        latestReleaseApiUrl = "https://api.github.com/repos/magisk317/xinyi-relay/releases/latest",
        defaultReleaseHtmlUrl = "https://github.com/magisk317/xinyi-relay/releases/latest",
        userAgent = "XinyiRelay",
    )

    private val smscodeConfig = GithubUpdateConfig(
        latestReleaseApiUrl = "https://smscode.usdt.edu.kg/repos/magisk317/XposedSmsCode/releases/latest",
        defaultReleaseHtmlUrl = "https://github.com/magisk317/XposedSmsCode/releases/latest",
        userAgent = "XposedSmsCode",
    )

    @Test
    fun buildReleaseRequest_appliesUserAgent() {
        val request = GithubUpdateChecker.buildReleaseRequest(smscodeConfig, smscodeConfig.latestReleaseApiUrl)
        assertEquals("XposedSmsCode", request.header("User-Agent"))
        assertEquals("application/vnd.github+json", request.header("Accept"))
    }

    @Test
    fun fetchLatestReleaseWithRequester_usesConfiguredApiUrl() {
        var requestedUrl: String? = null
        kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester(relayConfig) { apiUrl ->
                requestedUrl = apiUrl
                """{"tag_name":"v9.9.9","html_url":"https://example.com/release"}"""
            }
        }
        assertEquals(relayConfig.latestReleaseApiUrl, requestedUrl)
    }

    @Test
    fun parseLatestReleaseJson_parsesVersionAndUrl() {
        val json = """{"tag_name":"v3.1.1","html_url":"https://github.com/magisk317/XposedSmsCode/releases/tag/v3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(smscodeConfig, json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals(
            "https://github.com/magisk317/XposedSmsCode/releases/tag/v3.1.1",
            release?.htmlUrl,
        )
    }

    @Test
    fun parseLatestReleaseJson_usesConfigFallbackUrlWhenMissing() {
        val json = """{"tag_name":"V3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(relayConfig, json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals(relayConfig.defaultReleaseHtmlUrl, release?.htmlUrl)
    }

    @Test
    fun parseLatestReleaseJson_returnsNullWhenTagMissing() {
        val json = """{"name":"release-without-tag"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(relayConfig, json)

        assertNull(release)
    }

    @Test
    fun isNewer_comparesSemanticLikeVersions() {
        assertTrue(GithubUpdateChecker.isNewer("3.1.0", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.1", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.2", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("3.1", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("v3.1.0", "V3.2.0-beta1"))
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnRequesterFailure() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester(relayConfig) {
                throw IOException("network down")
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnMissingTag() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester(relayConfig) {
                """{"name":"release-without-tag"}"""
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsParsedReleaseOnSuccess() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester(relayConfig) {
                """{"tag_name":"v9.9.9","html_url":"https://github.com/magisk317/xinyi-relay/releases/tag/v9.9.9"}"""
            }
        }
        assertNotNull(release)
        assertEquals("9.9.9", release?.versionName)
    }

    @Test
    fun parseStructuredUpgradeJson_parsesNewFormat() {
        val json = """
            {
              "versionCode": 31800,
              "versionName": "3.1.8",
              "htmlUrl": "https://github.com/magisk317/XposedSmsCode/releases/tag/v3.1.8",
              "changelog": "fixes",
              "versionLogs": [{"name":"3.1.8","code":31800,"desc":"line1"}],
              "apks": [
                {"abi":"arm64-v8a","downloadUrl":"https://example.com/app-arm64.apk","fileSize":100,"sha256":"abcd"},
                {"abi":"universal","downloadUrl":"https://example.com/app-universal.apk","fileSize":200,"sha256":"efgh"}
              ],
              "signingCertSha256": "AA:BB"
            }
        """.trimIndent()

        val info = GithubUpdateChecker.parseStructuredUpgradeJson(smscodeConfig, json)

        assertNotNull(info)
        assertEquals(31800L, info?.versionCode)
        assertEquals("3.1.8", info?.versionName)
        assertEquals(2, info?.apks?.size)
        assertEquals("AA:BB", info?.signingCertSha256)
    }

    @Test
    fun parseStructuredUpgradeJson_infersXposedApiFlavorFromAssetName() {
        val json = """
            {
              "versionCode": 32000,
              "versionName": "3.2.0",
              "apks": [
                {"abi":"arm64-v8a","downloadUrl":"https://example.com/arm64v8a_legacy_app.apk"},
                {"abi":"arm64-v8a","downloadUrl":"https://example.com/arm64v8a_api101_app.apk"}
              ]
            }
        """.trimIndent()

        val info = GithubUpdateChecker.parseStructuredUpgradeJson(relayConfig, json)

        assertEquals("legacy", info?.apks?.get(0)?.xposedApiFlavor)
        assertEquals("api101", info?.apks?.get(1)?.xposedApiFlavor)
    }

    @Test
    fun parseUpgradeCheckResult_fallsBackToReleaseLink() {
        val json = """{"tag_name":"v3.1.9","html_url":"https://example.com/release"}"""
        val result = GithubUpdateChecker.parseUpgradeCheckResult(relayConfig, json)
        val releaseLink = result as? UpgradeCheckResult.ReleaseLink
        assertNotNull(releaseLink)
        assertEquals("3.1.9", releaseLink?.release?.versionName)
    }

    @Test
    fun selectBestApkForDevice_prefersExactAbiThenUniversal() {
        val apks = listOf(
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "a"),
            UpgradeApkAsset(abi = "universal", downloadUrl = "u"),
        )

        val arm64 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("arm64-v8a"),
        )
        assertEquals("a", arm64?.downloadUrl)

        val x86 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("x86_64"),
        )
        assertEquals("u", x86?.downloadUrl)
    }

    @Test
    fun selectBestApkForDevice_prefersMatchingXposedApiFlavorBeforeAbi() {
        val apks = listOf(
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "legacy", xposedApiFlavor = "legacy"),
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "api101", xposedApiFlavor = "api101"),
        )

        val selected = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("arm64-v8a"),
            requiredXposedApiFlavor = "legacy",
        )

        assertEquals("legacy", selected?.downloadUrl)
    }

    @Test
    fun selectBestApkForDevice_onlyFallsBackToUniversalWithinSameFlavor() {
        val apks = listOf(
            UpgradeApkAsset(abi = "universal", downloadUrl = "legacy-u", xposedApiFlavor = "legacy"),
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "api101-arm64", xposedApiFlavor = "api101"),
        )

        val selected = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("arm64-v8a"),
            requiredXposedApiFlavor = "legacy",
        )

        assertEquals("legacy-u", selected?.downloadUrl)
    }
}
