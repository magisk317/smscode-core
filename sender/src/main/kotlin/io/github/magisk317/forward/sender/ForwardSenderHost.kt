package io.github.magisk317.forward.sender

/** Host-owned namespace for the encrypted email OAuth2 credential store. */
data class OAuthCredentialNamespace(
    val prefsFile: String,
    val keyAlias: String,
)

/**
 * Host-owned Matrix login session identity. A stable device id keeps repeated logins from
 * piling up zombie devices on the homeserver.
 */
data class MatrixLoginIdentity(
    val deviceId: String,
    val displayName: String,
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
    val matrixLogin: MatrixLoginIdentity,
)

/** Matrix login identity supplied by the product; never inferred inside the shared sender. */
internal object ForwardMatrixLogin {
    private const val FALLBACK_DEVICE_ID = "FORWARD_SENDER"
    private const val FALLBACK_DISPLAY_NAME = "Forward Sender"

    @Volatile
    private var current: MatrixLoginIdentity? = null

    fun install(identity: MatrixLoginIdentity) {
        current = identity
    }

    fun reset() {
        current = null
    }

    val deviceId: String
        get() = current?.deviceId?.takeIf { it.isNotBlank() } ?: FALLBACK_DEVICE_ID

    val displayName: String
        get() = current?.displayName?.takeIf { it.isNotBlank() } ?: FALLBACK_DISPLAY_NAME
}
