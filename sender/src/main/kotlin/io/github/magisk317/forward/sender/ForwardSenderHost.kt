package io.github.magisk317.forward.sender

/** Host-owned namespace for the encrypted email OAuth2 credential store. */
data class OAuthCredentialNamespace(
    val prefsFile: String,
    val keyAlias: String,
)

/** Host-owned distribution coordinates for the optional E2EE plugin APK. */
data class E2eePluginRepository(
    val baseUrl: String,
    val fileNamePrefix: String,
) {
    fun fileName(version: String, abi: String): String = "${fileNamePrefix}_v${version}_$abi.apk"

    fun fileUrl(version: String, abi: String): String = "$baseUrl/$version/${fileName(version, abi)}"

    fun checksumUrl(version: String, abi: String): String = fileUrl(version, abi) + ".sha256"
}

/** Product-supplied values the shared sender runtime must not hardcode. */
data class ForwardSenderHostConfig(
    val oauthCredentials: OAuthCredentialNamespace,
    val e2eePluginRepository: E2eePluginRepository,
)
