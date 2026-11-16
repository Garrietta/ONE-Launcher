package dev.one.launcher.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the user's Premiumize API key in EncryptedSharedPreferences.
 * The key is never logged, never written to a plain file, and never
 * leaves the device except in requests to premiumize.me.
 *
 * The EncryptedSharedPreferences instance is lazy (v1.8.7): creating
 * it touches the AndroidKeyStore and does disk I/O, which delayed the
 * first Compose frame when it ran in the ViewModel constructor on the
 * main thread. All reads/writes run on Dispatchers.IO.
 */
class SecureKeyStore(context: Context) {
    private val prefs by lazy {
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

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)
    }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "premiumize_api_key"
    }
}
