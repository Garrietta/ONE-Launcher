package dev.one.launcher.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the user's Premiumize API key in EncryptedSharedPreferences.
 * The key is never logged, never written to a plain file, and never
 * leaves the device except in requests to premiumize.me.
 */
class SecureKeyStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "one_premiumize",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "premiumize_api_key"
    }
}
