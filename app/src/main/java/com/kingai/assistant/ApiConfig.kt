package com.kingai.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ذخیره امن تنظیمات API در گوشی.
 * توکن با کلید Android Keystore رمزگذاری می‌شود.
 */
object ApiConfig {

    private const val PREFS = "king_ai_api_config"
    private const val ALIAS = "king_ai_api_key_v1"

    private const val BASE_URL = "base_url"
    private const val MODEL = "model"
    private const val TOKEN_IV = "token_iv"
    private const val TOKEN_DATA = "token_data"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    fun saveToken(context: Context, token: String): Boolean {
        if (token.isBlank()) return false

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())

            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

            prefs(context).edit()
                .putString(
                    TOKEN_IV,
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                )
                .putString(
                    TOKEN_DATA,
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
                )
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun getToken(context: Context): String? {
        return try {
            val p = prefs(context)

            val ivText = p.getString(TOKEN_IV, null) ?: return null
            val dataText = p.getString(TOKEN_DATA, null) ?: return null

            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(dataText, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveEndpoint(
        context: Context,
        baseUrl: String,
        model: String
    ): Boolean {
        val url = baseUrl.trim().trimEnd('/')
        val modelName = model.trim()

        if (!url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        if (modelName.isBlank()) return false

        return prefs(context).edit()
            .putString(BASE_URL, url)
            .putString(MODEL, modelName)
            .commit()
    }

    fun getBaseUrl(context: Context): String =
        prefs(context).getString(BASE_URL, "") ?: ""

    fun getModel(context: Context): String =
        prefs(context).getString(MODEL, "") ?: ""

    fun hasToken(context: Context): Boolean =
        !getToken(context).isNullOrBlank()

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }
}
