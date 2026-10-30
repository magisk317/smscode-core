package io.github.magisk317.forward.sender

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.forward.security.AndroidKeystoreKeyProvider
import io.github.magisk317.forward.security.AesGcmEnvelopeCipher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android-backed [EmailOAuthCredentialStore]. Each value is an AES-256-GCM
 * envelope ([AesGcmEnvelopeCipher]) sealed by a non-exportable
 * AndroidKeyStore key, so secrets never appear in plaintext at rest. Preference
 * keys carry only the non-secret [EmailOAuthCredentials.credentialId].
 */
class AndroidEmailOAuthCredentialStore(
    context: Context,
    namespace: OAuthCredentialNamespace,
    private val prefs: SharedPreferences = context.getSharedPreferences(
        namespace.prefsFile,
        Context.MODE_PRIVATE,
    ),
) : EmailOAuthCredentialStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val cipher = AesGcmEnvelopeCipher(
        keyProvider = AndroidKeystoreKeyProvider(namespace.keyAlias),
        envelopeVersion = "v1",
    )
    // Domain-separates ciphertexts so envelopes cannot be transplanted between
    // preference files or replayed for another purpose.
    private val aad = "${context.packageName}|${namespace.prefsFile}|oauth2_cred|v1".toByteArray()

    override fun save(credentials: EmailOAuthCredentials) {
        prefs.edit()
            .putString(keyFor(credentials.credentialId), cipher.encrypt(json.encodeToString(credentials), aad))
            .apply()
    }

    override fun load(credentialId: String): EmailOAuthCredentials? {
        val key = keyFor(credentialId)
        val raw = prefs.getString(key, null) ?: return null
        // A Keystore key wiped by the system (or corrupted data) fails closed:
        // drop the stale envelope so the sender falls back to a fresh device-code
        // grant instead of surfacing unrecoverable credentials.
        val decrypted = runCatching { cipher.decrypt(raw, aad) }.getOrElse {
            prefs.edit().remove(key).apply()
            return null
        }
        return runCatching { json.decodeFromString<EmailOAuthCredentials>(decrypted) }.getOrNull()
    }

    override fun updateTokens(
        credentialId: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiryMs: Long,
    ) {
        val existing = load(credentialId) ?: return
        save(
            existing.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiryMs = tokenExpiryMs,
            ),
        )
    }

    override fun delete(credentialId: String) {
        prefs.edit().remove(keyFor(credentialId)).apply()
    }

    override fun exists(credentialId: String): Boolean {
        return prefs.contains(keyFor(credentialId))
    }

    private fun keyFor(credentialId: String): String = "oauth2_cred_$credentialId"

}
