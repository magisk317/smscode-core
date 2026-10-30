package io.github.magisk317.forward.sender

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the host seam for Matrix login sessions: the shared sender must never invent a
 * product device identity, and must keep a stable id once the product installs one.
 */
class MatrixLoginIdentityTest {
    @AfterEach
    fun tearDown() {
        ForwardMatrixLogin.reset()
    }

    @Test
    fun `falls back to a library-neutral identity when no host config was installed`() {
        assertEquals("FORWARD_SENDER", ForwardMatrixLogin.deviceId)
        assertEquals("Forward Sender", ForwardMatrixLogin.displayName)
    }

    @Test
    fun `uses the identity installed from the host config`() {
        SenderRuntimeInstaller.installMatrixLogin(
            ForwardSenderHostConfig(
                oauthCredentials = OAuthCredentialNamespace(prefsFile = "oauth", keyAlias = "oauth-alias"),
                e2eePluginRepository = E2eePluginRepository(
                    baseUrl = "https://example.invalid/repo",
                    fileNamePrefix = "plugin",
                ),
                matrixLogin = MatrixLoginIdentity(deviceId = "HOST_DEVICE", displayName = "HostApp"),
            ),
        )

        assertEquals("HOST_DEVICE", ForwardMatrixLogin.deviceId)
        assertEquals("HostApp", ForwardMatrixLogin.displayName)
    }

    @Test
    fun `blank host values fall back instead of sending an empty device id`() {
        ForwardMatrixLogin.install(MatrixLoginIdentity(deviceId = "  ", displayName = ""))

        assertEquals("FORWARD_SENDER", ForwardMatrixLogin.deviceId)
        assertEquals("Forward Sender", ForwardMatrixLogin.displayName)
    }
}
