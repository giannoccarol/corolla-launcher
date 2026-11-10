package io.github.giannoccarol.corollalauncher.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys, one per provider, encrypted with AES-GCM under a non-exportable Android Keystore
 * key. Ported from the owner's Monetine app (`data/settings/KeyVault.kt`).
 *
 * Keys cannot be moved from Monetine: its Keystore key never leaves that phone. They are typed
 * in again here, or seeded once from the build (see [seedFromBuild]).
 */
class KeyVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("assistant_vault", Context.MODE_PRIVATE)

    fun get(providerId: String): String? {
        val stored = prefs.getString(providerId, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun set(providerId: String, apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) remove(providerId) else prefs.edit { putString(providerId, encrypt(clean)) }
    }

    fun remove(providerId: String) = prefs.edit { remove(providerId) }

    /** "••••••••  a1b2", enough to recognise a key without showing it. */
    fun masked(providerId: String): String? = get(providerId)?.let { "••••••••  ${it.takeLast(4)}" }

    /**
     * Stores a key baked into the build from `local.properties`, the first time only, so it does
     * not have to be typed on the head unit. A key the driver set or removed later wins.
     */
    fun seedFromBuild(providerId: String, apiKey: String) {
        if (apiKey.isBlank() || prefs.getBoolean(SEEDED_FLAG, false)) return
        if (get(providerId) == null) set(providerId, apiKey)
        prefs.edit { putBoolean(SEEDED_FLAG, true) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "assistant_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        // Starts with a character no provider id can have, so it never clashes with one.
        const val SEEDED_FLAG = "#seeded_from_build"
    }
}
