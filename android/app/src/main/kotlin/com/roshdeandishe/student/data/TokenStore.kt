package com.roshdeandishe.student.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * نگه‌داری امنِ توکن JWT روی دستگاه - رمزنگاری با یک کلید AES-GCM که
 * Android Keystore خودش تولید/نگه می‌دارد (هرگز به‌صورت خام از Keystore
 * خارج نمی‌شود)، بایت‌های رمزشده + IV در Jetpack DataStore (Preferences)
 * ذخیره می‌شوند - نه EncryptedSharedPreferences (که در راهنمای فعلی
 * اندروید در حال کنارگذاشته‌شدن است و coroutine-native هم نیست).
 * <p>
 * AuthInterceptor باید هر Request را بدون تعلیق (non-suspend) بخواند -
 * به همین دلیل بعد از هر save/load، مقدار را در یک متغیر in-memory هم نگه
 * می‌داریم؛ [currentTokenOrNull] همیشه از همان کش می‌خواند، نه مستقیماً از
 * DataStore.
 */
class TokenStore(private val context: Context) {

    private companion object {
        const val KEY_ALIAS = "roshdeandishe_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val tokenKey = stringPreferencesKey("encrypted_token")
    private val ivKey = stringPreferencesKey("token_iv")

    @Volatile
    private var cachedToken: String? = null

    fun currentTokenOrNull(): String? = cachedToken

    /** یک‌بار موقع راه‌اندازی اپ صدا زده می‌شود تا کش از دیسک پر شود. */
    suspend fun loadIntoCache() {
        val prefs = context.authDataStore.data.first()
        val encoded = prefs[tokenKey]
        val ivEncoded = prefs[ivKey]
        if (encoded == null || ivEncoded == null) {
            cachedToken = null
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivEncoded, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP))
        cachedToken = String(decrypted, Charsets.UTF_8)
    }

    suspend fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        context.authDataStore.edit { prefs ->
            prefs[tokenKey] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs[ivKey] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }
        cachedToken = token
    }

    suspend fun clearToken() {
        context.authDataStore.edit { it.clear() }
        cachedToken = null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
