package app.genrix.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets encrypted with an AES key that lives in the Android Keystore and never
 * leaves this phone. Only the ciphertext is stored in app-private preferences.
 */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    fun read(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        // A key that can't be decrypted (e.g. the Keystore was reset) counts as missing.
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun write(name: String, value: String) {
        prefs.edit().putString(name, encrypt(value)).apply()
    }

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sealed)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        val plain = cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        return String(plain, Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val GEMINI_KEY = "gemini_api_key"
        const val PC_TOKEN = "pc_bridge_token"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "genrix_vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
